# Copy WebView2 runtime files to project
$srcRoot = "C:\Program Files (x86)\Microsoft\EdgeWebView\Application\151.0.4129.59"
$destRoot = "D:\Desktop\NotProjext\github-contribution-calendar-chart\preview\webview2_runtime"

# Copy msedgewebview2.exe
Copy-Item "$srcRoot\msedgewebview2.exe" $destRoot -Force
Write-Host "Copied msedgewebview2.exe"

# Copy EmbeddedBrowserWebView.dll
$destDll = Join-Path $destRoot "EBWebView\x64"
if (-not (Test-Path $destDll)) { New-Item -ItemType Directory -Path $destDll -Force | Out-Null }
Copy-Item "$srcRoot\EBWebView\x64\EmbeddedBrowserWebView.dll" $destDll -Force
Write-Host "Copied EmbeddedBrowserWebView.dll"

# Copy msedge.dll (325MB, the main browser engine)
Copy-Item "$srcRoot\msedge.dll" $destRoot -Force
Write-Host "Copied msedge.dll"

# Copy essential supporting files
$essentials = @(
    "concrt140.dll", "d3dcompiler_47.dll", "dxcompiler.dll", "dxil.dll",
    "ffmpeg.dll", "icudtl.dat", "msvcp140.dll", "msvcp140_codecvt_ids.dll",
    "vccorlib140.dll", "vcruntime140.dll", "vcruntime140_1.dll",
    "v8_context_snapshot.bin", "resources.pak", "msedge_100_percent.pak",
    "msedge_200_percent.pak"
)
foreach ($f in $essentials) {
    $src = Join-Path $srcRoot $f
    if (Test-Path $src) {
        Copy-Item $src $destRoot -Force
        Write-Host "Copied $f"
    }
}

# List final contents
Get-ChildItem $destRoot -Recurse | ForEach-Object {
    Write-Host "$($_.FullName) -> $([math]::Round($_.Length/1MB, 1)) MB"
}
