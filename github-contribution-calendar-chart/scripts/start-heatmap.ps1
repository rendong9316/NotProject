$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LogPath = Join-Path $ProjectRoot 'heatmap.log'
$NodePath = (Get-Command node -ErrorAction Stop).Source

if (Get-NetTCPConnection -LocalPort 4783 -State Listen -ErrorAction SilentlyContinue) {
    exit 0
}

Set-Location -LiteralPath $ProjectRoot
& $NodePath 'server/index.mjs' *>> $LogPath
