$ErrorActionPreference = 'Continue'
Set-Location 'D:\Desktop\NotProjext\github-contribution-calendar-chart\cpp'

# Kill any existing process
Get-Process git_local -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 500

# Run the program
$proc = Start-Process 'build\git_local.exe' -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 2

# Check if running
$running = Get-Process -Id $proc.Id -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "Running: PID=$($running.Id), Mem=$([math]::Round($running.WorkingSet64/1MB,1)) MB"
    $running.CloseMainWindow() | Out-Null
    Start-Sleep -Seconds 1
    if (-not $running.HasExited) { $running.Kill() | Out-Null }
} else {
    Write-Host "Process exited"
    Write-Host "Exit code: $($proc.ExitCode)"
}
