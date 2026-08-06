$procs = Get-Process | Where-Object { $_.ProcessName -match 'GitLocal|msedgewebview' }
foreach ($p in $procs) {
    Write-Host ("{0,-25} {1,6} MB" -f $p.ProcessName, [math]::Round($p.WorkingSet64/1MB, 1))
}
$total = ($procs | Measure-Object -Property WorkingSet64 -Sum).Sum
Write-Host ("Total: {0:N1} MB" -f ($total/1MB))
