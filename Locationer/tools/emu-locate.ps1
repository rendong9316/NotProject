# tools/emu-locate.ps1 —— 将宿主机（本电脑）位置注入 Android 模拟器
# 用法：powershell -ExecutionPolicy Bypass -File tools\emu-locate.ps1
# 原理：模拟器虚拟 GPS 无传感器，无法自行感知宿主机位置；
#       本脚本通过 IP 定位获取本机城市级坐标（WGS84），经 adb 注入模拟器，
#       回到 App 点击右下角准星即可定位到本机位置。
$ErrorActionPreference = 'Stop'

# ---------- 1. 定位 adb 可执行文件（优先读取工程 local.properties） ----------
$adb = $null
$props = Join-Path $PSScriptRoot '..\local.properties'
if (Test-Path $props) {
    $line = Get-Content $props | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($line) {
        $sdk = ($line.Substring(8)) -replace '\\:', ':' -replace '\\\\', '\'
        $cand = Join-Path $sdk 'platform-tools\adb.exe'
        if (Test-Path $cand) { $adb = $cand }
    }
}
if (-not $adb) { $adb = 'adb' }

# ---------- 2. 检查已连接的模拟器 ----------
$devices = & $adb devices | Where-Object { $_ -match 'emulator-\d+\s+device' }
if (-not $devices) {
    Write-Host '未发现已连接的模拟器：请先启动 AVD 并等待系统就绪' -ForegroundColor Red
    exit 1
}

# ---------- 3. 获取宿主机公共网络位置（IP 定位，城市级精度） ----------
$lat = $null
$lon = $null
$name = ''
try {
    $loc = Invoke-RestMethod -Uri 'http://ip-api.com/json/?lang=zh-CN' -TimeoutSec 10
    if ($loc.status -eq 'success') {
        $lat = $loc.lat
        $lon = $loc.lon
        $name = (@($loc.city, $loc.regionName) -join ' ').Trim()
    }
} catch { }
if (-not $lat) {
    try {
        $loc = Invoke-RestMethod -Uri 'https://ipinfo.io/json' -TimeoutSec 10
        $parts = $loc.loc -split ','
        $lat = $parts[0]
        $lon = $parts[1]
        $name = (@($loc.city, $loc.region) -join ' ').Trim()
    } catch { }
}
if (-not $lat -or -not $lon) {
    Write-Host '获取本机位置失败：请检查网络；或改用模拟器 Extended Controls -> Location 手动输入坐标' -ForegroundColor Yellow
    exit 1
}

# ---------- 4. 向所有已连接模拟器注入坐标并校验结果 ----------
foreach ($d in $devices) {
    $emu = ($d -split '\s+')[0]
    & $adb -s $emu emu geo fix $lon $lat | Out-Null

    # 校验注入是否生效：读取系统 GPS 最后 fix 并比对
    Start-Sleep -Seconds 1
    $dump = (& $adb -s $emu shell dumpsys location 2>$null) -join "`n"
    $m = [regex]::Match($dump, 'last location=Location\[gps ([-\d.]+),([-\d.]+)')
    if ($m.Success) {
        $actualLat = [double]$m.Groups[1].Value
        $actualLon = [double]$m.Groups[2].Value
        $delta = [math]::Abs($actualLat - $lat) + [math]::Abs($actualLon - $lon)
        if ($delta -gt 0.01) {
            Write-Host "警告：$emu 的 GPS 通道未响应注入（当前 fix 为 $actualLon,$actualLat）。" -ForegroundColor Yellow
            Write-Host '原因通常是模拟器运行过久/快照恢复导致 GPS 通道挂死：请重启模拟器（冷启动 AVD）后重新运行本脚本。' -ForegroundColor Yellow
            continue
        }
    }
    Write-Host "已向 $emu 注入本机位置 [$name]：经度 $lon，纬度 $lat" -ForegroundColor Green
}
Write-Host '回到 App 点击右下角准星按钮即可定位到本机位置。' -ForegroundColor Cyan
Write-Host '注意：IP 定位为城市级精度（数百米~数公里）；如需精确到门牌，请用模拟器 Location 面板手动输入坐标。' -ForegroundColor Yellow