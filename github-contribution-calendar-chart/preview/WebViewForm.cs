using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

namespace WebViewPreview;

public class WebViewForm : Form
{
    // ── WebView2 COM Interfaces ────────────────────────────────────────────

    [ComImport, Guid("3050F4E7-98B7-4DF6-BB4E-F7AC75F98B68")]
    private class WebView2EnvironmentClass { }

    [ComImport, Guid("B9D4D12B-8E85-4C38-86F0-C89E34E5C8F6")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IWebView2Environment
    {
        void CreateCoreWebView2Controller(IntPtr parentWindowHandle,
            IWebView2CreateCoreWebView2ControllerCompletedHandler completionHandler);
        void CreateCoreWebView2EnvironmentWithOptions(string? browserExecutableFolder,
            string? userDataFolder, string? additionalBrowserArguments,
            IWebView2CreateCoreWebView2EnvironmentCompletedHandler completionHandler);
        void GetBrowserVersionInfo(out IWebView2BrowserVersionInfo versionInfo);
        void AddWebResourceRequestedFilter(string urlFilter, WebView2WebResourceContext resourceContext);
        void RemoveWebResourceRequestedFilter(string urlFilter, WebView2WebResourceContext resourceContext);
        void Close();
    }

    [ComImport, Guid("84F5A3FD-4C3D-4E56-9F7A-2B3C4D5E6F70")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IWebView2CreateCoreWebView2EnvironmentCompletedHandler
    {
        void Invoke(int hr, IWebView2Environment? environment);
    }

    [ComImport, Guid("D43F6E6F-83B3-4B69-AB0E-0C4F1D2C7B8A")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IWebView2CreateCoreWebView2ControllerCompletedHandler
    {
        void Invoke(int hr, IWebView2Controller? controller);
    }

    [ComImport, Guid("5BFFF8AC-7A30-4335-A643-180EB7A37E8D")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IWebView2Controller
    {
        void put_IsVisible(bool value);
        void get_IsVisible(out bool value);
        void put_Bounds(RECT value);
        void get_Bounds(out RECT value);
        void put_ZoomFactor(float value);
        void get_ZoomFactor(out float value);
        void Navigate(string? url);
        void NavigateToString(string? html);
        void Close();
    }

    [ComImport, Guid("A3F2B1C0-D9E8-4756-B8C7-A6F5E4D3C2B1")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IWebView2BrowserVersionInfo
    {
        void get_ProductVersion(out string version);
        void get_FileVersion(out string version);
        void get_FullVersion(out string version);
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct RECT { public int Left, Top, Right, Bottom; }

    internal enum WebView2WebResourceContext : uint { All = 0, Document = 1 }

    // ── P/Invoke ───────────────────────────────────────────────────────────

    [DllImport("ole32.dll")]
    private static extern int CoCreateInstance(ref Guid clsid, IntPtr pUnkOuter,
        uint dwClsContext, ref Guid riid, out IntPtr ppv);

    // ── Fields ─────────────────────────────────────────────────────────────

    private IWebView2Environment? _env;
    private IWebView2Controller? _controller;
    private Label? _lblStatus;

    public WebViewForm()
    {
        Text = "WebView2 AI 对话预览";
        Size = new Size(960, 720);
        MinimumSize = new Size(640, 480);
        StartPosition = FormStartPosition.CenterScreen;
        FormClosing += WebViewForm_FormClosing;
        Shown += async (s, e) => await InitAsync();
    }

    private async Task InitAsync()
    {
        _lblStatus = new Label
        {
            Dock = DockStyle.Bottom, Height = 28,
            Font = new Font("Microsoft YaHei UI", 9f),
            Text = "正在初始化 WebView2...",
            BackColor = SystemColors.Control,
            BorderStyle = BorderStyle.FixedSingle,
            Padding = new Padding(8, 0, 0, 0)
        };
        Controls.Add(_lblStatus);

        try
        {
            // Find WebView2 runtime
            var progFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
            var runtimeDir = Path.Combine(progFiles, "Microsoft", "EdgeWebView", "Application");
            var versionDir = Directory.GetDirectories(runtimeDir).OrderByDescending(d => d).FirstOrDefault();
            var exePath = versionDir != null
                ? Path.Combine(versionDir, "msedgewebview2.exe")
                : null;

            // Try COM registration
            if (exePath != null && File.Exists(exePath))
            {
                try
                {
                    var psi = new ProcessStartInfo(exePath, "--type=reg-chrome")
                    { UseShellExecute = false, CreateNoWindow = true, WindowStyle = ProcessWindowStyle.Hidden };
                    Process.Start(psi)?.WaitForExit(3000);
                }
                catch { }
            }

            // Create WebView2 environment
            var clsid = new Guid("3050F4E7-98B7-4DF6-BB4E-F7AC75F98B68");
            var guidEmpty = Guid.Empty;
            var pEnv = IntPtr.Zero;
            int hr = CoCreateInstance(ref clsid, IntPtr.Zero, 0x1, ref guidEmpty, out pEnv);

            if (hr < 0)
            {
                _lblStatus.Text = $"CoCreateInstance 失败 0x{hr:X8}。请确保已安装 Edge WebView2 Runtime。";
                return;
            }

            _env = Marshal.GetTypedObjectForIUnknown(pEnv, typeof(IWebView2Environment)) as IWebView2Environment;
            if (_env == null) { _lblStatus.Text = "无法获取 IWebView2Environment 接口"; return; }

            _lblStatus.Text = "WebView2 环境已创建，正在初始化控制器...";

            var ctrlReady = new TaskCompletionSource<IWebView2Controller>();
            _env.CreateCoreWebView2Controller(this.Handle,
                new CtrlHandler(ctrlReady));

            _controller = await ctrlReady.Task;
            _lblStatus.Text = "WebView2 就绪！";

            LoadFrontend();
        }
        catch (Exception ex)
        {
            _lblStatus.Text = $"错误: {ex.Message}";
        }
    }

    private void LoadFrontend()
    {
        var indexPath = Path.Combine(AppContext.BaseDirectory, "frontend", "dist", "index.html");
        if (File.Exists(indexPath))
            _controller?.NavigateToString(File.ReadAllText(indexPath, Encoding.UTF8));
        else
            _controller?.NavigateToString(CreateDemoHtml());
    }

    private string CreateDemoHtml()
    {
        return @"<!DOCTYPE html>
<html lang='zh-CN'>
<head>
  <meta charset='UTF-8'>
  <title>WebView2 AI 对话预览</title>
  <script src='https://unpkg.com/vue@3/dist/vue.global.prod.js'><\/script>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: 'Microsoft YaHei UI', sans-serif; background: #f3f4f6; height: 100vh; display: flex; flex-direction: column; }
    .header { background: linear-gradient(135deg, #1e3a5f, #2563eb); color: white; padding: 14px 20px; display: flex; align-items: center; gap: 12px; }
    .header h1 { font-size: 16px; font-weight: 600; flex: 1; }
    .badge { background: rgba(255,255,255,0.2); padding: 3px 10px; border-radius: 12px; font-size: 11px; }
    .chat { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
    .msg { max-width: 82%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.65; word-break: break-word; }
    .msg.user { align-self: flex-end; background: #2563eb; color: white; border-bottom-right-radius: 4px; }
    .msg.assistant { align-self: flex-start; background: white; border: 1px solid #e5e7eb; border-bottom-left-radius: 4px; }
    .msg.system { align-self: center; background: #f3f4f6; color: #6b7280; font-size: 12px; padding: 6px 14px; border-radius: 20px; }
    .input { padding: 12px 16px; background: white; border-top: 1px solid #e5e7eb; display: flex; gap: 8px; }
    .input textarea { flex: 1; padding: 10px 14px; border: 1px solid #d1d5db; border-radius: 10px; font-size: 14px; resize: none; outline: none; font-family: inherit; min-height: 42px; }
    .input textarea:focus { border-color: #2563eb; }
    .input button { padding: 10px 20px; background: #2563eb; color: white; border: none; border-radius: 10px; font-size: 14px; cursor: pointer; }
    .input button:disabled { background: #9ca3af; }
    .hint { font-size: 11px; color: #9ca3af; padding: 4px 16px 0; text-align: center; }
    .copy-btn { position: fixed; padding: 5px 14px; background: #2563eb; color: white; border: none; border-radius: 6px; font-size: 13px; cursor: pointer; z-index: 1000; display: none; box-shadow: 0 2px 8px rgba(0,0,0,0.2); }
    .copy-btn.show { display: block; }
  </style>
</head>
<body>
<div id='app'>
  <div class='header'>
    <span>🤖</span><h1>AI 助手预览</h1><span class='badge'>WebView2 原型</span>
  </div>
  <div class='chat' ref='chatArea'>
    <div v-for='(m,i) in msgs' :key='i' class='msg' :class='m.kind'>{{ m.text }}</div>
    <div v-if='busy' class='msg assistant'>思考中...</div>
  </div>
  <div class='hint'>Enter 发送 · Shift+Enter 换行 · 选中文本后点击复制按钮</div>
  <div class='input'>
    <textarea v-model='input' @keydown='onKey' placeholder='输入消息...'></textarea>
    <button :disabled='busy' @click='send'>{{ busy ? '停止' : '发送' }}</button>
  </div>
  <button class='copy-btn' id='copyBtn' @click='copy'>复制</button>
</div>
<script>
const {createApp, ref, onMounted, nextTick} = Vue;
createApp({
  setup() {
    const msgs = ref([{kind:'system',text:'WebView2 原型环境已就绪'}]);
    const input = ref(''); const busy = ref(false);
    const chatArea = ref(null);

    onMounted(() => {
      msgs.value.push({kind:'assistant',text:'你好！这是 WebView2 原生宿主 + Vue 对话界面原型。文本选中后可点击复制按钮，功能正常。'});
      nextTick(()=>chatArea.value.scrollTop=chatArea.value.scrollHeight);
    });

    function onKey(e) { if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send();} }
    function send() {
      const t=input.value.trim(); if(!t||busy.value)return;
      msgs.value.push({kind:'user',text:t}); input.value=''; busy.value=true;
      nextTick(()=>chatArea.value.scrollTop=chatArea.value.scrollHeight);
      setTimeout(()=>{
        msgs.value.push({kind:'assistant',text:'这是 WebView2 原型演示。文本选中、复制、回车发送等功能已全部可用。实际对接时将连接 GitLocal.Agent 后端通过命名管道通信。'});
        busy.value=false; nextTick(()=>chatArea.value.scrollTop=chatArea.value.scrollHeight);
      },800);
    }
    document.addEventListener('selectionchange',()=>{
      const sel=window.getSelection(); const txt=sel?sel.toString().trim():'';
      const btn=document.getElementById('copyBtn');
      if(txt.length>0&&btn){
        const r=sel.getRangeAt(0).getBoundingClientRect();
        btn.style.left=Math.max(0,r.right-70)+'px';
        btn.style.top=Math.max(0,r.top-34)+'px';
        btn.style.display='block';
      }else if(btn){btn.style.display='none';}
    });
    function copy(){
      const sel=window.getSelection().toString();
      if(sel){navigator.clipboard.writeText(sel).then(()=>{
        const btn=document.getElementById('copyBtn');
        if(btn){btn.textContent='已复制 ✓';setTimeout(()=>{btn.textContent='复制';btn.style.display='none';},1200);}
      });}
    }
    return {msgs,input,busy,chatArea,onKey,send,copy};
  }
}).mount('#app');
<\/script>
</body>
</html>";
    }

    private void WebViewForm_FormClosing(object? sender, FormClosingEventArgs e)
    {
        _controller?.Close();
        _env?.Close();
    }
}

// ── COM handler wrappers ────────────────────────────────────────────────

internal class CtrlHandler : WebViewPreview.WebViewForm.IWebView2CreateCoreWebView2ControllerCompletedHandler
{
    private readonly TaskCompletionSource<WebViewPreview.WebViewForm.IWebView2Controller> _tcs;
    public CtrlHandler(TaskCompletionSource<WebViewPreview.WebViewForm.IWebView2Controller> tcs) { _tcs = tcs; }
    public void Invoke(int hr, WebViewPreview.WebViewForm.IWebView2Controller? ctrl)
    {
        if (hr >= 0 && ctrl != null) _tcs.SetResult(ctrl);
        else _tcs.SetException(new Exception($"Controller create failed: 0x{hr:X8}"));
    }
}
