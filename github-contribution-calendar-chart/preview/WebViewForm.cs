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
            _controller?.NavigateToString("<!DOCTYPE html><html><body></body></html>");
    }

    private string CreateDemoHtml()
    {
        return "<!DOCTYPE html><html><body></body></html>";
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
