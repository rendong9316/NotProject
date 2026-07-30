$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ServerScript = Join-Path $PSScriptRoot 'start-heatmap.ps1'

if (-not (Get-NetTCPConnection -LocalPort 4783 -State Listen -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath 'powershell.exe' `
        -ArgumentList '-NoProfile', '-WindowStyle', 'Hidden', '-ExecutionPolicy', 'Bypass', '-File', "`"$ServerScript`"" `
        -WorkingDirectory $ProjectRoot `
        -WindowStyle Hidden

    for ($Attempt = 0; $Attempt -lt 30; $Attempt++) {
        Start-Sleep -Milliseconds 300
        if (Get-NetTCPConnection -LocalPort 4783 -State Listen -ErrorAction SilentlyContinue) {
            break
        }
    }
}

Start-Process 'http://localhost:4783'
