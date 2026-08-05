# Check NuGet source availability and download WebView2 package
$ErrorActionPreference = 'Continue'

# Try downloading from CDN (Mirrors work in China)
$mirrors = @(
    "https://mirrors.tencent.com/nuget/",
    "https://repo.huaweicloud.com/nuget/",
    "https://api.nuget.org/v3/"
)

foreach ($mirror in $mirrors) {
    Write-Host "Trying: $mirror"
    try {
        $url = $mirror + "v3-flatcontainer/microsoft.webview2.winforms/index.json"
        $req = [System.Net.WebRequest]::Create($url)
        $req.Timeout = 5000
        $resp = $req.GetResponse()
        $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $content = $sr.ReadToEnd()
        $resp.Close()
        Write-Host "OK - $mirror"
        Write-Host $content.Substring(0, [Math]::Min(200, $content.Length))
        break
    } catch {
        Write-Host "FAIL: $_"
    }
}
