# Check memory usage of WebView2 related processes
$procs = Get-Process | Where-Object {
    $_.ProcessName -match 'WebViewPreview|Edge|WebView|msedgewebview'
} | Select-Object Id, ProcessName, @{N='MemMB';E={[math]::Round($_.WorkingSet64/1MB,1)}}
$procs | Format-Table -AutoSize

# Check total
$total = ($procs | Measure-Object -Property MemMB -Sum).Sum
Write-Host ""
Write-Host "Total WebView2 related memory: $([math]::Round($total,1)) MB"
