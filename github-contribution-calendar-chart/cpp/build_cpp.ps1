$ErrorActionPreference = 'Stop'
Set-Location 'D:\Desktop\NotProjext\github-contribution-calendar-chart\cpp'

$cl = 'C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.36.32532\bin\Hostx64\x64\cl.exe'
$link = 'C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.36.32532\bin\Hostx64\x64\link.exe'
$rc = 'C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\rc.exe'
$msvcInclude = 'C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.36.32532\include'
$msvcLib = 'C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.36.32532\lib\x64'
$sdkInclude = 'C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0'
$sdkLib = 'C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\um\x64'

$env:INCLUDE = $msvcInclude + ';' + $sdkInclude + '\shared;' + $sdkInclude + '\um;' + $sdkInclude + '\winrt;' + $sdkInclude + '\ucrt'
$env:LIB = $msvcLib + ';' + $sdkLib + ';' + 'C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\shared' + ';' + 'C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\ucrt\x64'
$env:PATH = 'C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64;' + $env:PATH

$common = @('/nologo', '/std:c++17', '/O2', '/EHsc', '/MT', '/utf-8', '/permissive-', '/W4',
    '/DUNICODE', '/D_UNICODE', '/D_CRT_SECURE_NO_WARNINGS', '/DNOMINMAX', '/c')

$srcs = @('json','platform','cache_store','git_scan','state','ai_tools','ai_agent','ui_draw','ui_control','entry')

foreach ($s in $srcs) {
    Write-Host "[$s]"
    $outFile = 'obj\' + $s + '.obj'
    $srcFile = 'src\' + $s + '.cpp'
    $errFile = 'obj\' + $s + '.err'
    $result = & $cl $common $srcFile "/Fo$outFile" 2> $errFile
    if ($result) { Write-Host $result }
    $errContent = Get-Content $errFile -Raw -ErrorAction SilentlyContinue
    if ($errContent -match 'error' -or $LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $s"
        if ($errContent) { Write-Host $errContent }
        exit 1
    }
    Remove-Item $errFile -ErrorAction SilentlyContinue
}

Write-Host '[rc]'
& $rc /nologo /fo obj\app_res.res src\app.rc 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host 'RC FAILED'; exit 1 }

Write-Host '[link]'
$linkArgs = @('/nologo', '/OUT:build\git_local.exe', '/SUBSYSTEM:WINDOWS', '/MANIFEST:EMBED',
    '/DYNAMICBASE', '/NXCOMPAT', '/MACHINE:X64',
    'obj\json.obj', 'obj\platform.obj', 'obj\cache_store.obj', 'obj\git_scan.obj',
    'obj\state.obj', 'obj\ai_tools.obj', 'obj\ai_agent.obj', 'obj\ui_draw.obj',
    'obj\ui_control.obj', 'obj\entry.obj', 'obj\app_res.res',
    'gdi32.lib', 'gdiplus.lib', 'comctl32.lib', 'shell32.lib', 'shlwapi.lib',
    'user32.lib', 'advapi32.lib', 'ole32.lib', 'bcrypt.lib')
$lnkOut = & $link @linkArgs 2>&1 | Out-String
Write-Host $lnkOut
if ($LASTEXITCODE -ne 0) { Write-Host 'LINK FAILED'; exit 1 }

Write-Host 'Build successful!'
$f = Get-Item 'build\git_local.exe'
Write-Host "Size: $([math]::Round($f.Length/1KB, 1)) KB"
