@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
pushd "%PROJECT_DIR%"
if not exist build mkdir build

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
set "VCVARS="
if exist "%VSWHERE%" for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VCVARS=%%i\VC\Auxiliary\Build\vcvars64.bat"
if not defined VCVARS if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat" set "VCVARS=C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
if not defined VCVARS (
    echo ERROR: Visual Studio C++ build tools were not found.
    popd
    exit /b 1
)
call "%VCVARS%" >nul 2>&1
if errorlevel 1 (
    echo ERROR: Failed to initialize the MSVC x64 environment.
    popd
    exit /b 1
)

set "COMMON=/nologo /std:c++17 /O2 /EHsc /MT /utf-8 /permissive- /W4 /DUNICODE /D_UNICODE /D_CRT_SECURE_NO_WARNINGS /DNOMINMAX /c"
set "SOURCES=json platform cache_store git_scan state ui_draw ui_control entry"

for %%s in (%SOURCES%) do (
    echo [CXX] %%s.cpp
    cl %COMMON% "src\%%s.cpp" /Fo"build\%%s.obj"
    if errorlevel 1 goto :error
)

echo [RC ] app.rc
rc /nologo /fo "build\app_res.res" "src\app.rc"
if errorlevel 1 goto :error

echo [LINK] git_local.exe
link /nologo /OUT:"build\git_local.exe" /SUBSYSTEM:WINDOWS /MANIFEST:EMBED /DYNAMICBASE /NXCOMPAT /MACHINE:X64 ^
    build\json.obj build\platform.obj build\cache_store.obj build\git_scan.obj ^
    build\state.obj build\ui_draw.obj build\ui_control.obj build\entry.obj build\app_res.res ^
    gdi32.lib gdiplus.lib comctl32.lib shell32.lib shlwapi.lib user32.lib advapi32.lib ole32.lib bcrypt.lib
if errorlevel 1 goto :error

echo.
echo Build successful: %PROJECT_DIR%build\git_local.exe
for %%f in (build\git_local.exe) do echo Size: %%~zf bytes

powershell -ExecutionPolicy Bypass -Command "$s=[Environment]::GetFolderPath('Desktop'); $p=Join-Path $s 'Git Local.lnk'; $w=New-Object -ComObject WScript.Shell; $sh=$w.CreateShortcut($p); $sh.TargetPath='%PROJECT_DIR%build\git_local.exe'; $sh.WorkingDirectory='%PROJECT_DIR%build'; $sh.Description='Git Local'; $sh.IconLocation='%PROJECT_DIR%build\git_local.exe,0'; $sh.Save(); Write-Host '[LNK] Desktop shortcut updated'"

popd
exit /b 0

:error
echo.
echo Build FAILED.
popd
exit /b 1
