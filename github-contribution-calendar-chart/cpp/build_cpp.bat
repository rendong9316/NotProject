@echo off
setlocal

set "TOOLCHAIN=C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.36.32532\bin\Hostx64\x64"
set "MSVC=%TOOLCHAIN%"
set "SDK=C:\Program Files (x86)\Windows Kits\10"
set "SDK_VER=10.0.26100.0"

set "INCLUDE=%MSVC%\include;%SDK%\Include\%SDK_VER%\shared;%SDK%\Include\%SDK_VER%\um;%SDK%\Include\%SDK_VER%\winrt;%SDK%\Include\%SDK_VER%\ucrt"
set "LIB=%MSVC%\lib\x64;%SDK%\Lib\%SDK_VER%\um\x64;%SDK%\Lib\%SDK_VER%\shared"

set "CL=%TOOLCHAIN%\cl.exe"
set "LINK=%TOOLCHAIN%\link.exe"
set "RC=%SDK%\bin\%SDK_VER%\x64\rc.exe"

set "COMMON=/nologo /std:c++17 /O2 /EHsc /MT /utf-8 /permissive- /W4 /DUNICODE /D_UNICODE /D_CRT_SECURE_NO_WARNINGS /DNOMINMAX /c"

if not exist obj mkdir obj
if not exist build mkdir build

echo [json]
%CL% %COMMON% src\json.cpp /Foobj\json.obj
if errorlevel 1 goto error

echo [platform]
%CL% %COMMON% src\platform.cpp /Foobj\platform.obj
if errorlevel 1 goto error

echo [cache_store]
%CL% %COMMON% src\cache_store.cpp /Foobj\cache_store.obj
if errorlevel 1 goto error

echo [git_scan]
%CL% %COMMON% src\git_scan.cpp /Foobj\git_scan.obj
if errorlevel 1 goto error

echo [state]
%CL% %COMMON% src\state.cpp /Foobj\state.obj
if errorlevel 1 goto error

echo [ai_tools]
%CL% %COMMON% src\ai_tools.cpp /Foobj\ai_tools.obj
if errorlevel 1 goto error

echo [ai_agent]
%CL% %COMMON% src\ai_agent.cpp /Foobj\ai_agent.obj
if errorlevel 1 goto error

echo [ui_draw]
%CL% %COMMON% src\ui_draw.cpp /Foobj\ui_draw.obj
if errorlevel 1 goto error

echo [ui_control]
%CL% %COMMON% src\ui_control.cpp /Foobj\ui_control.obj
if errorlevel 1 goto error

echo [entry]
%CL% %COMMON% src\entry.cpp /Foobj\entry.obj
if errorlevel 1 goto error

echo [rc]
%RC% /nologo /fo obj\app_res.res src\app.rc
if errorlevel 1 goto error

echo [link]
%LINK% /nologo /OUT:build\git_local.exe /SUBSYSTEM:WINDOWS /MANIFEST:EMBED /DYNAMICBASE /NXCOMPAT /MACHINE:X64 ^
    obj\json.obj obj\platform.obj obj\cache_store.obj obj\git_scan.obj ^
    obj\state.obj obj\ai_tools.obj obj\ai_agent.obj obj\ui_draw.obj obj\ui_control.obj obj\entry.obj obj\app_res.res ^
    gdi32.lib gdiplus.lib comctl32.lib shell32.lib shlwapi.lib user32.lib advapi32.lib ole32.lib bcrypt.lib
if errorlevel 1 goto error

echo.
echo Build successful: build\git_local.exe
for %%f in (build\git_local.exe) do echo Size: %%~zf bytes
endlocal
exit /b 0

:error
echo.
echo Build FAILED
endlocal
exit /b 1
