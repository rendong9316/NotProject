#include "state.h"
#include "ui_draw.h"
#include "ui_control.h"
#include "config.h"
#include <windows.h>
#include <gdiplus.h>
#include <random>

#ifndef IDI_ICON
#define IDI_ICON 101
#endif

using namespace Gdiplus;

int main() {
    // 初始化随机数引擎
    std::random_device rd;
    rng.seed(rd());

    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    if (user32) {
        typedef BOOL (WINAPI *SetDpiAwareFn)();
        SetDpiAwareFn setDpiAware = (SetDpiAwareFn)GetProcAddress(user32, "SetProcessDPIAware");
        if (setDpiAware) setDpiAware();
    }
    std::wstring error;
    if (!LoadQuestionBank(RES_ID_SINGLE, MODE_SINGLE, g_banks[0], error) ||
        !LoadQuestionBank(RES_ID_MULTIPLE, MODE_MULTIPLE, g_banks[1], error) ||
        !LoadQuestionBank(RES_ID_FILL, MODE_FILL, g_banks[2], error)) {
        MessageBoxW(nullptr, error.c_str(), CFG_ERR_LOAD_FAILED, MB_OK | MB_ICONERROR);
        return 1;
    }
    g_state.page = PAGE_HOME;

    GdiplusStartupInput gdiplusStartupInput;
    ULONG_PTR gdiplusToken;
    GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, nullptr);

    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(WNDCLASSEXW);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = GetModuleHandleW(nullptr);
    HINSTANCE hInst = GetModuleHandleW(nullptr);
    wc.hCursor = LoadCursorW(hInst, IDC_ARROW);
    wc.hIcon = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 32, 32, LR_DEFAULTSIZE);
    wc.hIconSm = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 16, 16, LR_DEFAULTSIZE);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.lpszClassName = CFG_CLASS_NAME;
    RegisterClassExW(&wc);

    HWND hwnd = CreateWindowExW(0, CFG_CLASS_NAME, CFG_APP_TITLE, WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, INITIAL_WINDOW_W, INITIAL_WINDOW_H, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!hwnd) {
        GdiplusShutdown(gdiplusToken);
        return 1;
    }

    ShowWindow(hwnd, SW_MAXIMIZE);
    UpdateWindow(hwnd);

    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    GdiplusShutdown(gdiplusToken);
    return 0;
}
