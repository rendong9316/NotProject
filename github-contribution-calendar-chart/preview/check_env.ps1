# Check EmbeddedBrowserWebView.dll
$ffi = 'C:\Program Files (x86)\Microsoft\EdgeWebView\Application\151.0.4129.59\EBWebView\x64\EmbeddedBrowserWebView.dll'
$f = Get-Item $ffi
Write-Host "Size: $([math]::Round($f.Length/1MB, 1)) MB"

# Try loading and inspecting types
try {
    $asm = [System.Reflection.Assembly]::LoadFile($ffi)
    Write-Host "Assembly loaded successfully"
    $asm.GetTypes() | Where-Object { $_.Name -notlike "Internal*" -and $_.Name -notlike "<*" } | Select-Object -First 30 Name, Namespace
} catch {
    Write-Host "Load error: $_"
}

# Check msedgewebview2.exe
$exe = 'C:\Program Files (x86)\Microsoft\EdgeWebView\Application\151.0.4129.59\msedgewebview2.exe'
Write-Host "msedgewebview2.exe exists: $(Test-Path $exe)"
