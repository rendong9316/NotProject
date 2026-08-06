using System.Diagnostics;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;

namespace GitLocalWebView2Host;

public class WebViewHostForm : Form
{
    // ── WebView2 COM ───────────────────────────────────────────────────────

    [ComImport, Guid("3050F4E7-98B7-4DF6-BB4E-F7AC75F98B68")]
    private class WebView2EnvClass { }

    [ComImport, Guid("B9D4D12B-8E85-4C38-86F0-C89E34E5C8F6")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IEnv
    {
        void CreateCoreWebView2Controller(IntPtr hwnd, ICtrlDone handler);
        void Close();
    }

    [ComImport, Guid("D43F6E6F-83B3-4B69-AB0E-0C4F1D2C7B8A")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ICtrlDone
    {
        void Invoke(int hr, IController? ctrl);
    }

    [ComImport, Guid("5BFFF8AC-7A30-4335-A643-180EB7A37E8D")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IController
    {
        void NavigateToString(string? html);
        void Close();
    }

    [DllImport("ole32.dll")]
    private static extern int CoCreateInstance(ref Guid c, IntPtr o, uint ctx, ref Guid r, out IntPtr p);

    // ── State ──────────────────────────────────────────────────────────────

    private IEnv? _env;
    private IController? _ctrl;
    private Label? _status;
    private Button? _stopBtn;
    private readonly string _pipeName;
    private NamedPipeClientStream? _pipe;
    private StreamReader? _sr;
    private StreamWriter? _sw;
    private CancellationTokenSource? _cts;

    public WebViewHostForm(string pipeName)
    {
        _pipeName = pipeName;
        Text = "GitLocal AI 助手";
        Size = new Size(860, 620);
        MinimumSize = new Size(540, 420);
        StartPosition = FormStartPosition.CenterScreen;
        FormClosing += (s, e) => OnFormClosing();

        _status = new Label
        {
            Dock = DockStyle.Bottom, Height = 28,
            Font = new Font("Microsoft YaHei UI", 9f),
            Text = "初始化中...",
            BackColor = SystemColors.Control,
            BorderStyle = BorderStyle.FixedSingle,
            Padding = new Padding(8, 0, 0, 0)
        };
        Controls.Add(_status);

        _stopBtn = new Button
        {
            Dock = DockStyle.Bottom, Height = 32,
            Text = "停止", Visible = false,
            Font = new Font("Microsoft YaHei UI", 9f),
            BackColor = Color.FromArgb(220, 53, 69),
            ForeColor = Color.White, FlatStyle = FlatStyle.Flat
        };
        _stopBtn.Click += (s, e) => CancelRequest();
        Controls.Add(_stopBtn);

        Shown += async (s, e) => await InitAsync();
    }

    private async Task InitAsync()
    {
        // Connect named pipe
        _pipe = new NamedPipeClientStream(".", _pipeName, PipeDirection.InOut, PipeOptions.Asynchronous);
        await _pipe.ConnectAsync(5000);
        _sr = new StreamReader(_pipe, Encoding.UTF8);
        _sw = new StreamWriter(_pipe) { NewLine = "\n" };
        _cts = new CancellationTokenSource();
        _ = Task.Run(() => ReadLoop(_cts.Token));

        // Create WebView2 via COM
        var clsid = new Guid("3050F4E7-98B7-4DF6-BB4E-F7AC75F98B68");
        var pEnv = IntPtr.Zero;
        var gEmpty = Guid.Empty;
        int hr = CoCreateInstance(ref clsid, IntPtr.Zero, 0x1, ref gEmpty, out pEnv);

        if (hr < 0)
        {
            Invoke(() => _status!.Text = $"WebView2 初始化失败 0x{hr:X8}");
            return;
        }

        _env = Marshal.GetTypedObjectForIUnknown(pEnv, typeof(IEnv)) as IEnv;
        if (_env == null) { _status!.Text = "无法获取环境接口"; return; }

        var tcs = new TaskCompletionSource<IController>();
        _env.CreateCoreWebView2Controller(this.Handle, new CtrlDoneHandler(tcs));
        _ctrl = await tcs.Task;

        Invoke(() => { _status!.Text = "就绪"; });
        LoadFrontend();
    }

    private void LoadFrontend()
    {
        var indexPath = Path.Combine(AppContext.BaseDirectory, "frontend", "dist", "index.html");
        string html;
        if (File.Exists(indexPath))
            html = File.ReadAllText(indexPath, Encoding.UTF8);
        else
            html = CreateDemoHtml();
        _ctrl!.NavigateToString(html);
    }

    private async void ReadLoop(CancellationToken ct)
    {
        try
        {
            while (!ct.IsCancellationRequested)
            {
                var line = await _sr!.ReadLineAsync();
                if (line == null) break;
                try { DispatchMessage(line); } catch { }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            Invoke(() => _status!.Text = $"通信错误: {ex.Message}");
        }
    }

    private void DispatchMessage(string line)
    {
        using var doc = JsonDocument.Parse(line);
        var root = doc.RootElement;

        if (root.TryGetProperty("method", out var method))
        {
            var m = method.GetString() ?? "";
            var p = root.TryGetProperty("params", out var paramsEl) ? paramsEl : default(JsonElement);
            if (m == "chat.delta" && p.TryGetProperty("text", out var textEl))
            {
                var text = textEl.GetString() ?? "";
                Invoke(() => RunJs($"window.__delta({Escape(text)})"));
            }
            else if (m == "chat.completed" && p.TryGetProperty("text", out var t2))
            {
                var text = t2.GetString() ?? "";
                Invoke(() =>
                {
                    _stopBtn!.Visible = false;
                    _status!.Text = "助手";
                    RunJs($"window.__complete({Escape(text)})");
                });
            }
            else if (m == "chat.failed" && p.TryGetProperty("message", out var errMsg))
            {
                var msg = errMsg.GetString() ?? "未知错误";
                Invoke(() =>
                {
                    _stopBtn!.Visible = false;
                    _status!.Text = "连接异常";
                    RunJs($"window.__error({Escape(msg)})");
                });
            }
            else if (m == "chat.tool_started" && p.TryGetProperty("name", out var toolName))
            {
                var name = toolName.GetString() ?? "";
                Invoke(() => RunJs($"window.__toolStart({Escape(name)})"));
            }
        }
    }

    private void RunJs(string js)
    {
        _ctrl?.NavigateToString("<script>" + js + "</" + "script>");
    }

    private async Task SendChatAsync(string message)
    {
        Invoke(() =>
        {
            _stopBtn!.Visible = true;
            _status!.Text = "正在思考...";
        });
        await SendJsonAsync(new
        {
            jsonrpc = "2.0",
            method = "chat.start",
            @params = new
            {
                sessionId = "main",
                turnId = Guid.NewGuid().ToString("N"),
                message,
                context = new { activeYear = 2025, selectedDate = "", selectedRepositories = new object[0] }
            }
        });
    }

    private void CancelRequest()
    {
        _ = Task.Run(async () =>
        {
            await SendJsonAsync(new
            {
                jsonrpc = "2.0",
                method = "chat.cancel",
                @params = new { turnId = "current" }
            });
        });
        Invoke(() =>
        {
            _stopBtn!.Visible = false;
            _status!.Text = "已停止";
        });
    }

    private async Task SendJsonAsync(object msg)
    {
        if (_sw == null || _pipe == null || !_pipe.IsConnected) return;
        var json = JsonSerializer.Serialize(msg);
        await _sw.WriteLineAsync(json);
        await _sw.FlushAsync();
    }

    private string Escape(string s)
    {
        return "\"" + s
            .Replace("\\", "\\\\")
            .Replace("\"", "\\\"")
            .Replace("\n", "\\n")
            .Replace("\r", "\\r")
            + "\"";
    }

    private void OnFormClosing()
    {
        _cts?.Cancel();
        try { _sr?.Close(); } catch { }
        try { _sw?.Close(); } catch { }
        try { _pipe?.Close(); } catch { }
        try { _env?.Close(); } catch { }
    }

    private string CreateDemoHtml()
    {
        return @"<!DOCTYPE html>
<html lang='zh-CN'><head>
<meta charset='UTF-8'><title>AI 助手</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Microsoft YaHei UI',sans-serif;background:#f3f4f6;height:100vh;display:flex;flex-direction:column}
.header{background:linear-gradient(135deg,#1e3a5f,#2563eb);color:white;padding:14px 20px;display:flex;align-items:center;gap:12px}
.header h1{font-size:16px;font-weight:600;flex:1}
.badge{background:rgba(255,255,255,0.2);padding:3px 10px;border-radius:12px;font-size:11px}
.chat{flex:1;overflow-y:auto;padding:16px;display:flex;flex-direction:column;gap:10px}
.msg{max-width:82%;padding:10px 14px;border-radius:12px;font-size:14px;line-height:1.65;word-break:break-word}
.msg.user{align-self:flex-end;background:#2563eb;color:white;border-bottom-right-radius:4px}
.msg.assistant{align-self:flex-start;background:white;border:1px solid #e5e7eb;border-bottom-left-radius:4px}
.msg.system{align-self:center;background:#f3f4f6;color:#6b7280;font-size:12px;padding:6px 14px;border-radius:20px}
.input{padding:12px 16px;background:white;border-top:1px solid #e5e7eb;display:flex;gap:8px}
.input textarea{flex:1;padding:10px 14px;border:1px solid #d1d5db;border-radius:10px;font-size:14px;resize:none;outline:none;font-family:inherit;min-height:42px}
.input textarea:focus{border-color:#2563eb}
.input button{padding:10px 20px;background:#2563eb;color:white;border:none;border-radius:10px;font-size:14px;cursor:pointer}
.input button:disabled{background:#9ca3af}
.hint{font-size:11px;color:#9ca3af;padding:4px 16px 0;text-align:center}
.copy-btn{position:fixed;padding:5px 14px;background:#2563eb;color:white;border:none;border-radius:6px;font-size:13px;cursor:pointer;z-index:1000;display:none;box-shadow:0 2px 8px rgba(0,0,0,0.2)}
.copy-btn.show{display:block}
</style>
</head><body>
<div id='app'>
<div class='header'><span>🤖</span><h1>AI 助手</h1><span class='badge' id='badge'>连接中</span></div>
<div class='chat' id='chatArea'><div class='msg system' id='sysMsg'>等待连接...</div></div>
<div class='hint'>Enter 发送 · Shift+Enter 换行 · 选中文本后点击复制</div>
<div class='input'><textarea id='input' placeholder='输入消息...' rows='2'></textarea><button id='sendBtn' onclick='send()'>发送</button></div>
<button class='copy-btn' id='copyBtn' onclick='copy()'>复制</button>
</div>
<script>
const chatArea=document.getElementById('chatArea');
const input=document.getElementById('input');
const sendBtn=document.getElementById('sendBtn');
const copyBtn=document.getElementById('copyBtn');
const badge=document.getElementById('badge');
const sysMsg=document.getElementById('sysMsg');
let isBusy=false;

window.__onConnect=function(ready){
  sysMsg.textContent=ready?'AI 服务已连接':'等待连接...';
  badge.textContent=ready?'已连接':'连接中';
  badge.style.background=ready?'rgba(34,197,94,0.3)':'rgba(255,255,255,0.2)';
};
window.__delta=function(text){
  let msg=document.getElementById('cur');
  if(!msg){msg=document.createElement('div');msg.id='cur';msg.className='msg assistant';chatArea.appendChild(msg);}
  msg.textContent+=text;
  chatArea.scrollTop=chatArea.scrollHeight;
};
window.__complete=function(text){isBusy=false;sendBtn.disabled=false;sendBtn.textContent='发送';chatArea.scrollTop=chatArea.scrollHeight;};
window.__error=function(err){isBusy=false;sendBtn.disabled=false;sendBtn.textContent='发送';addMsg('system','错误: '+err);};
window.__toolStart=function(name){addMsg('system','调用工具: '+name);};

function addMsg(kind,text){
  const d=document.createElement('div');d.className='msg '+kind;d.textContent=text;
  chatArea.appendChild(d);chatArea.scrollTop=chatArea.scrollHeight;
}

function send(){
  const text=input.value.trim();if(!text||isBusy)return;
  addMsg('user',text);input.value='';isBusy=true;sendBtn.disabled=true;sendBtn.textContent='停止';
  if(window.chrome&&window.chrome.webview)window.chrome.webview.postMessage(JSON.stringify({type:'chat',message:text}));
  else if(window.nativeHost)window.nativeHost.sendChat(text);
}

input.addEventListener('keydown',function(e){if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send();}});

document.addEventListener('selectionchange',function(){
  const sel=window.getSelection();const txt=sel?sel.toString().trim():'';
  if(txt.length>0){const r=sel.getRangeAt(0).getBoundingClientRect();
    copyBtn.style.left=Math.max(0,r.right-70)+'px';copyBtn.style.top=Math.max(0,r.top-34)+'px';
    copyBtn.style.display='block';}else{copyBtn.style.display='none';}
});

function copy(){
  const txt=window.getSelection().toString();
  if(txt){navigator.clipboard.writeText(txt).then(function(){
    copyBtn.textContent='已复制 ✓';
    setTimeout(function(){copyBtn.textContent='复制';copyBtn.style.display='none';},1200);
  });}
}

setTimeout(function(){window.__onConnect(true);},300);
</script>
</body></html>";
    }
}

internal class CtrlDoneHandler : WebViewHostForm.ICtrlDone
{
    private readonly TaskCompletionSource<WebViewHostForm.IController> _tcs;
    public CtrlDoneHandler(TaskCompletionSource<WebViewHostForm.IController> tcs) { _tcs = tcs; }
    public void Invoke(int hr, WebViewHostForm.IController? ctrl)
    {
        if (hr >= 0 && ctrl != null) _tcs.SetResult(ctrl);
        else _tcs.SetException(new Exception($"CreateController failed: 0x{hr:X8}"));
    }
}
