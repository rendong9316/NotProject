#include "config.h"
#include "json.h"
#include "git_scan.h"
#include "platform.h"
#include "state.h"

#include <windows.h>
#include <gdiplus.h>
#include <commctrl.h>
#include <algorithm>
#include <cstdio>
#include <string>

namespace {

void EnsureUsableWindowSize(HWND window) {
    RECT rect = {};
    if (!GetWindowRect(window, &rect)) return;
    const int width = rect.right - rect.left;
    const int height = rect.bottom - rect.top;
    if (width >= 960 && height >= 680) return;
    SetWindowPos(window, nullptr, rect.left, rect.top, std::max(width, 960), std::max(height, 680),
                 SWP_NOACTIVATE | SWP_NOZORDER);
}

int RunDiagnostics() {
    bool passed = true;
    Json value;
    std::string parseError;
    const std::string sample = "{\"name\":\"测试\",\"enabled\":true,\"days\":{\"2026-08-01\":3}}";
    if (!Json::Parse(sample, value, parseError) || !value.get("enabled").boolean() ||
        value.get("days").get("2026-08-01").integer() != 3) {
        std::printf("FAIL json: %s\n", parseError.c_str());
        passed = false;
    } else std::printf("PASS json parse/serialize\n");

    const std::wstring root = ApplicationDataDirectory();
    std::wstring error;
    const std::wstring testDirectory = JoinPath(root, L"diagnostics");
    const std::wstring testFile = JoinPath(testDirectory, L"utf8.json");
    const std::string content = u8"{\"中文\":\"路径与提交\"}\n";
    std::string readBack;
    if (!WriteUtf8FileAtomic(testFile, content, error) || !ReadUtf8File(testFile, readBack, error) || readBack != content) {
        std::printf("FAIL filesystem: %s\n", WideToUtf8(error).c_str());
        passed = false;
    } else std::printf("PASS utf8 atomic filesystem\n");
    DeleteFileW(testFile.c_str());
    RemoveDirectoryW(testDirectory.c_str());

    std::wstring gitVersion;
    if (!GitScan::IsGitAvailable(gitVersion, error)) {
        std::printf("FAIL git: %s\n", WideToUtf8(error).c_str());
        passed = false;
    } else std::printf("PASS %s\n", WideToUtf8(gitVersion).c_str());

    const std::wstring projectIndex = FindProjectFile(L"data\\index.json");
    if (projectIndex.empty()) {
        std::printf("WARN development cache not found\n");
    } else {
        std::string indexText;
        Json index;
        if (!ReadUtf8File(projectIndex, indexText, error) || !Json::Parse(indexText, index, parseError) ||
            !index.get("repositories").isArray()) {
            std::printf("FAIL existing cache compatibility\n");
            passed = false;
        } else std::printf("PASS existing cache: %zu repositories\n", index.get("repositories").array().size());
    }
    return passed ? 0 : 1;
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, LPWSTR commandLine, int showCommand) {
    if (commandLine && std::wstring(commandLine).find(L"--diagnose") != std::wstring::npos) {
        AttachConsole(ATTACH_PARENT_PROCESS);
        FILE* stream = nullptr;
        freopen_s(&stream, "CONOUT$", "w", stdout);
        return RunDiagnostics();
    }

    HANDLE instanceMutex = CreateMutexW(nullptr, TRUE, L"LocalGitHeatmap.SingleInstance.v2");
    if (instanceMutex && GetLastError() == ERROR_ALREADY_EXISTS) {
        HWND existing = FindWindowW(APP_CLASS, nullptr);
        if (existing) {
            ShowWindow(existing, SW_RESTORE);
            EnsureUsableWindowSize(existing);
            SetForegroundWindow(existing);
        }
        CloseHandle(instanceMutex);
        return 0;
    }

    SetProcessDPIAware();
    INITCOMMONCONTROLSEX controls = {sizeof(INITCOMMONCONTROLSEX), ICC_STANDARD_CLASSES};
    InitCommonControlsEx(&controls);
    Gdiplus::GdiplusStartupInput input;
    ULONG_PTR gdiplusToken = 0;
    if (Gdiplus::GdiplusStartup(&gdiplusToken, &input, nullptr) != Gdiplus::Ok) return 1;

    WNDCLASSEXW windowClass = {};
    windowClass.cbSize = sizeof(windowClass);
    windowClass.style = CS_HREDRAW | CS_VREDRAW;
    windowClass.lpfnWndProc = WndProc;
    windowClass.hInstance = instance;
    windowClass.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    windowClass.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
    windowClass.hIconSm = static_cast<HICON>(LoadImageW(nullptr, IDI_APPLICATION, IMAGE_ICON, 16, 16, LR_SHARED));
    windowClass.hbrBackground = nullptr;
    windowClass.lpszClassName = APP_CLASS;
    if (!RegisterClassExW(&windowClass)) return 1;

    HWND window = CreateWindowExW(0, APP_CLASS, APP_TITLE, WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN,
                                  CW_USEDEFAULT, CW_USEDEFAULT, 1240, 820, nullptr, nullptr, instance, nullptr);
    if (!window) {
        Gdiplus::GdiplusShutdown(gdiplusToken);
        return 1;
    }
    ApplyDarkMode(window, false);
    ShowWindow(window, showCommand);
    UpdateWindow(window);

    MSG message = {};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    Gdiplus::GdiplusShutdown(gdiplusToken);
    if (instanceMutex) { ReleaseMutex(instanceMutex); CloseHandle(instanceMutex); }
    return static_cast<int>(message.wParam);
}
