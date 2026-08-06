using System.Diagnostics;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Text;

namespace GitLocalWebView2Host;

static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        string pipeName = "GitLocal.Agent.WebView";
        if (args.Length > 0) pipeName = args[0];
        Application.Run(new WebViewHostForm(pipeName));
    }
}
