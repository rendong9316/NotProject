// ui_control.cpp — 窗口消息分发（WndProc）、点击处理、控件管理
#include "ui_control.h"
#include "ui_draw.h"
#include "state.h"
#include "config.h"
#include <windows.h>
#include <string>
#include <algorithm>

#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif
#ifndef DWMWA_CAPTION_COLOR
#define DWMWA_CAPTION_COLOR 35
#endif
#ifndef DWMWA_TEXT_COLOR
#define DWMWA_TEXT_COLOR 36
#endif
#ifndef GET_X_LPARAM
#define GET_X_LPARAM(lp) ((int)(short)LOWORD(lp))
#endif
#ifndef GET_Y_LPARAM
#define GET_Y_LPARAM(lp) ((int)(short)HIWORD(lp))
#endif
#ifndef IDI_ICON
#define IDI_ICON 101
#endif

// Global handles
HWND g_hwndEdit = nullptr;
HFONT g_hfontEdit = nullptr;
HWND g_hwndMain = nullptr;

// Forward declarations (defined in ui_draw.cpp)
bool Hit(int mx, int my, int x, int y, int w, int h);
void AdjustFont(float delta);

// ===================== Edit Control =====================

std::wstring TrimString(const std::wstring& s) {
    size_t a = s.find_first_not_of(L" \t\r\n");
    size_t b = s.find_last_not_of(L" \t\r\n");
    if (a == std::wstring::npos) return L"";
    return s.substr(a, b - a + 1);
}

void ApplyEditFont() {
    if (!g_hwndEdit) return;
    int px = (int)(18 * g_fontScale + 0.5f);
    if (g_hfontEdit) DeleteObject(g_hfontEdit);
    g_hfontEdit = CreateFontW(-px, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                              DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                              CLEARTYPE_QUALITY, FF_DONTCARE, CFG_FONT_FAMILY);
    SendMessageW(g_hwndEdit, WM_SETFONT, (WPARAM)g_hfontEdit, TRUE);
}

void EnsureEditControl(HWND parent) {
    if (g_hwndEdit) return;
    g_hwndEdit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
                                 WS_CHILD | ES_AUTOHSCROLL,
                                 0, 0, 100, 30, parent, nullptr,
                                 GetModuleHandleW(nullptr), nullptr);
    ApplyEditFont();
}

void UpdateEditForState() {
    if (!g_hwndEdit) return;
    bool shouldShow = g_state.page == PAGE_QUIZ
                      && g_state.mode == MODE_FILL
                      && !g_state.answered;
    if (shouldShow) {
        ReadEditIntoState();
        UIRect r = FillEditRect();
        SetWindowPos(g_hwndEdit, HWND_TOP,
                     ScaleCoord(r.x), ScaleCoord(r.y),
                     ScaleCoord(r.w), ScaleCoord(r.h),
                     SWP_SHOWWINDOW | SWP_NOACTIVATE);
        if (g_state.userFill.empty()) {
            SetWindowTextW(g_hwndEdit, L"");
        } else {
            SetWindowTextW(g_hwndEdit, g_state.userFill.c_str());
        }
    } else {
        if (g_state.page != PAGE_QUIZ || !g_state.answered) {
            g_state.userFill.clear();
        }
        ShowWindow(g_hwndEdit, SW_HIDE);
    }
}

void DestroyEditControl() {
    if (g_hwndEdit) {
        DestroyWindow(g_hwndEdit);
        g_hwndEdit = nullptr;
    }
    if (g_hfontEdit) {
        DeleteObject(g_hfontEdit);
        g_hfontEdit = nullptr;
    }
}

void ReadEditIntoState() {
    if (!g_hwndEdit) return;
    int len = GetWindowTextLengthW(g_hwndEdit);
    if (len <= 0) { g_state.userFill.clear(); return; }
    std::wstring buf(len + 1, L'\0');
    int got = GetWindowTextW(g_hwndEdit, &buf[0], len + 1);
    if (got < 0) got = 0;
    buf.resize(got);
    g_state.userFill = buf;
}

// ===================== Dark Title Bar =====================

void ApplyTitleBarStyle(HWND hwnd) {
    HMODULE dwm = LoadLibraryW(L"dwmapi.dll");
    if (!dwm) return;
    typedef HRESULT (WINAPI *DwmSetWindowAttributeFn)(HWND, DWORD, LPCVOID, DWORD);
    DwmSetWindowAttributeFn setAttr = (DwmSetWindowAttributeFn)GetProcAddress(dwm, "DwmSetWindowAttribute");
    if (setAttr) {
        BOOL dark = TRUE;
        COLORREF caption = RGB(13, 42, 86);
        COLORREF text = RGB(255, 255, 255);
        setAttr(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark, sizeof(dark));
        setAttr(hwnd, DWMWA_CAPTION_COLOR, &caption, sizeof(caption));
        setAttr(hwnd, DWMWA_TEXT_COLOR, &text, sizeof(text));
    }
    FreeLibrary(dwm);
}

// ===================== Global Font/Size Click =====================

void HandleGlobalClick(int mx, int my) {
    bool changed = false;
    if (Hit(mx, my, g_w - 154, 16, 48, 32)) { AdjustFont(-0.06f); changed = true; }
    if (Hit(mx, my, g_w - 98, 16, 48, 32)) { AdjustFont(0.06f); changed = true; }
    if (changed) { ApplyEditFont(); UpdateEditForState(); }
}

// ===================== Click Handlers =====================

static void HandleHomePageClick(int mx, int my, HWND hwnd) {
    for (int i = 0; i < 3; ++i) {
        UIRect r = HomeButtonRect(i);
        if (Hit(mx, my, r.x, r.y, r.w, r.h)) {
            if (i == 2) OpenFillScoreSelect();
            else StartQuiz(i == 0 ? MODE_SINGLE : MODE_MULTIPLE);
            UpdateEditForState();
            if (g_state.page == PAGE_QUIZ && g_state.mode == MODE_FILL && g_hwndEdit) {
                SetFocus(g_hwndEdit);
            }
            InvalidateRect(hwnd, nullptr, FALSE);
            return;
        }
    }
}

static void HandleFillScoreSelectClick(int mx, int my, HWND hwnd) {
    UIRect backRect = ReturnHomeButtonRect();
    if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
        g_state.resetQuiz(ActiveBank());
        g_state.page = PAGE_HOME;
        UpdateEditForState();
        InvalidateRect(hwnd, nullptr, FALSE);
        return;
    }
    for (int i = NO_SCORE; i < FILL_SCORE_OPTION_COUNT; ++i) {
        UIRect scoreRect = FillScoreButtonRect(i);
        if (Hit(mx, my, scoreRect.x, scoreRect.y, scoreRect.w, scoreRect.h)) {
            StartFillQuiz(FILL_SCORE_OPTIONS[i], i);
            UpdateEditForState();
            if (g_hwndEdit) SetFocus(g_hwndEdit);
            InvalidateRect(hwnd, nullptr, FALSE);
            return;
        }
    }
}

static void HandleQuizPageClick(int mx, int my, HWND hwnd) {
    UIRect backRect = ReturnHomeButtonRect();
    if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
        int rc = MessageBoxW(hwnd,
            CFG_CONFIRM_MESSAGE,
            CFG_CONFIRM_TITLE, MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2);
        if (rc == IDYES) {
            g_state.resetQuiz(ActiveBank());
            g_state.page = PAGE_HOME;
            UpdateEditForState();
            SetFocus(hwnd);
        }
        InvalidateRect(hwnd, nullptr, FALSE);
        return;
    }
    if (IsTimedMode() && !g_state.answered && QuestionRemainingSeconds() <= 0) {
        SettleCurrentQuestion(hwnd, true);
    }
    // Option click handlers (only for non-fill modes)
    if (g_state.mode != MODE_FILL) {
        for (int i = NO_SCORE; i < g_choiceOptionRectCount; ++i) {
            const UIRect& optionRect = g_choiceOptionRects[i];
            if (!g_state.answered
                && Hit(mx, my, optionRect.x, optionRect.y,
                       optionRect.w, optionRect.h)) {
                if (g_state.mode == MODE_SINGLE) {
                    g_state.selected[0] = g_state.selected[1] = g_state.selected[2] = g_state.selected[3] = false;
                    g_state.selected[i] = true;
                } else {
                    g_state.selected[i] = !g_state.selected[i];
                }
                InvalidateRect(hwnd, nullptr, FALSE);
                return;
            }
        }
    }
    // Confirm / Next button
    UIRect confirmRect = ConfirmButtonRect();
    if (Hit(mx, my, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h)) {
        if (!g_state.answered) {
            // Just confirmed — read input then settle
            if (g_state.mode == MODE_FILL) {
                ReadEditIntoState();
                if (TrimString(g_state.userFill).empty()) {
                    MessageBoxW(hwnd, CFG_FILL_PROMPT, CFG_DLG_TITLE, MB_OK | MB_ICONINFORMATION);
                    return;
                }
            } else if (!HasSelection()) {
                MessageBoxW(hwnd, CFG_NO_SELECTION, CFG_DLG_TITLE, MB_OK | MB_ICONINFORMATION);
                return;
            }
            SettleCurrentQuestion(hwnd, false);
            UpdateEditForState();
            SetFocus(hwnd);
        } else if (g_state.qNum >= g_state.total) {
            // Last question done → show results
            g_state.quizEnd = std::chrono::system_clock::now();
            g_state.page = PAGE_RESULT;
            UpdateEditForState();
        } else {
            // Next question — clear edit and reset UI before picking new question
            ++g_state.qNum;
            // Clear edit box content from previous question to avoid residue
            if (g_hwndEdit) SetWindowTextW(g_hwndEdit, L"");
            g_choiceOptionRectCount = 0;
            if (!StartQuestion()) {
                --g_state.qNum;
                MessageBoxW(hwnd, CFG_ERR_SINGLE_EXHAUSTED, CFG_DLG_ERR, MB_OK | MB_ICONERROR);
            }
            UpdateEditForState();
            if (g_state.mode == MODE_FILL && g_hwndEdit) SetFocus(g_hwndEdit);
        }
        InvalidateRect(hwnd, nullptr, FALSE);
    }
}

static void HandleResultPageClick(int mx, int my, HWND hwnd) {
    int cw = std::min(g_w - 80, 760), ch = 500, cx = (g_w - cw) / 2, cy = 120;
    if (Hit(mx, my, cx + cw / 2 - 210, cy + ch - 82, 190, 48)) {
        if (g_state.mode == MODE_FILL) OpenFillScoreSelect();
        else StartQuiz(g_state.mode);
        UpdateEditForState();
        if (g_state.page == PAGE_QUIZ && g_state.mode == MODE_FILL && g_hwndEdit) {
            SetFocus(g_hwndEdit);
        }
    } else if (Hit(mx, my, cx + cw / 2 + 20, cy + ch - 82, 190, 48)) {
        g_state.resetQuiz(ActiveBank());
        g_state.page = PAGE_HOME;
        UpdateEditForState();
    }
    InvalidateRect(hwnd, nullptr, FALSE);
}

// ===================== WndProc =====================

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_SETCURSOR: {
        DWORD dwMousePos = GetMessagePos();
        short mx = (short)(GET_X_LPARAM(dwMousePos) / g_fontScale);
        short my = (short)(GET_Y_LPARAM(dwMousePos) / g_fontScale);

        if (g_state.page == PAGE_HOME) {
            for (int i = 0; i < 3; ++i) {
                UIRect r = HomeButtonRect(i);
                if (Hit(mx, my, r.x, r.y, r.w, r.h)) {
                    SetCursor(LoadCursorW(nullptr, IDC_HAND));
                    return TRUE;
                }
            }
        } else if (g_state.page == PAGE_FILL_SCORE_SELECT) {
            UIRect backRect = ReturnHomeButtonRect();
            if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
            for (int i = NO_SCORE; i < FILL_SCORE_OPTION_COUNT; ++i) {
                UIRect scoreRect = FillScoreButtonRect(i);
                if (Hit(mx, my, scoreRect.x, scoreRect.y, scoreRect.w, scoreRect.h)) {
                    SetCursor(LoadCursorW(nullptr, IDC_HAND));
                    return TRUE;
                }
            }
        } else if (g_state.page == PAGE_QUIZ) {
            UIRect backRect = ReturnHomeButtonRect();
            if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
            if (g_state.mode != MODE_FILL) {
                for (int i = NO_SCORE; i < g_choiceOptionRectCount; ++i) {
                    const UIRect& optionRect = g_choiceOptionRects[i];
                    if (!g_state.answered
                        && Hit(mx, my, optionRect.x, optionRect.y,
                               optionRect.w, optionRect.h)) {
                        SetCursor(LoadCursorW(nullptr, IDC_HAND));
                        return TRUE;
                    }
                }
            }
            UIRect confirmRect = ConfirmButtonRect();
            if (Hit(mx, my, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
        } else if (g_state.page == PAGE_RESULT) {
            int cw = std::min(g_w - 80, 760), ch = 500, cx = (g_w - cw) / 2, cy = 120;
            if (Hit(mx, my, cx + cw / 2 - 210, cy + ch - 82, 190, 48)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
            if (Hit(mx, my, cx + cw / 2 + 20, cy + ch - 82, 190, 48)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
        }
        SetCursor(LoadCursorW(nullptr, IDC_ARROW));
        return TRUE;
    }
    case WM_CREATE: {
        g_hwndMain = hwnd;
        ApplyTitleBarStyle(hwnd);
        SetTimer(hwnd, 1, 1000, nullptr);
        EnsureEditControl(hwnd);

        HINSTANCE hInst = GetModuleHandleW(nullptr);
        HICON hBig = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 32, 32, LR_DEFAULTSIZE);
        HICON hSmall = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 16, 16, LR_DEFAULTSIZE);
        if (hBig) SendMessageW(hwnd, WM_SETICON, ICON_BIG, (LPARAM)hBig);
        if (hSmall) SendMessageW(hwnd, WM_SETICON, ICON_SMALL, (LPARAM)hSmall);

        return 0;
    }
    case WM_SIZE: {
        g_clientW = std::max(720, (int)LOWORD(lParam));
        g_clientH = std::max(560, (int)HIWORD(lParam));
        UpdateLayoutSize();
        ResizeBackBuffer(hwnd);
        UpdateEditForState();
        return 0;
    }
    case WM_MOUSEWHEEL: {
        if (GetKeyState(VK_CONTROL) & 0x8000) {
            short z = GET_WHEEL_DELTA_WPARAM(wParam);
            AdjustFont(z > 0 ? 0.06f : -0.06f);
            ApplyEditFont();
            UpdateEditForState();
            return 0;
        }
        break;
    }
    case WM_TIMER: {
        if (g_state.page == PAGE_QUIZ && IsTimedMode() && !g_state.answered) {
            int remaining = QuestionRemainingSeconds();
            if (remaining <= 0) {
                SettleCurrentQuestion(hwnd, true);
                UpdateEditForState();
                SetFocus(hwnd);
            } else if (remaining <= 5) {
                static int flashTick = 0;
                flashTick++;
                g_state.flashVisible = (flashTick % 2 == 0);
                InvalidateRect(hwnd, nullptr, FALSE);
            } else {
                g_state.flashVisible = false;
                InvalidateRect(hwnd, nullptr, FALSE);
            }
        }
        return 0;
    }
    case WM_LBUTTONDOWN: {
        int mx = (int)(GET_X_LPARAM(lParam) / g_fontScale);
        int my = (int)(GET_Y_LPARAM(lParam) / g_fontScale);
        HandleGlobalClick(mx, my);

        switch (g_state.page) {
            case PAGE_HOME: HandleHomePageClick(mx, my, hwnd); break;
            case PAGE_FILL_SCORE_SELECT: HandleFillScoreSelectClick(mx, my, hwnd); break;
            case PAGE_QUIZ: HandleQuizPageClick(mx, my, hwnd); break;
            case PAGE_RESULT: HandleResultPageClick(mx, my, hwnd); break;
        }
        return 0;
    }
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);
        RECT rc;
        GetClientRect(hwnd, &rc);
        int clientW = rc.right - rc.left, clientH = rc.bottom - rc.top;
        if (!g_hdcMem) {
            g_hdcMem = CreateCompatibleDC(hdc);
            g_hbmMem = CreateCompatibleBitmap(hdc, clientW, clientH);
            SelectObject(g_hdcMem, g_hbmMem);
        }
        Graphics memGfx(g_hdcMem);
        memGfx.SetSmoothingMode(SmoothingModeAntiAlias);
        memGfx.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
        memGfx.SetCompositingQuality(CompositingQualityHighQuality);
        memGfx.ScaleTransform(g_fontScale, g_fontScale);
        if (g_state.page == PAGE_HOME) DrawHomePage(memGfx);
        else if (g_state.page == PAGE_FILL_SCORE_SELECT) DrawFillScoreSelectPage(memGfx);
        else if (g_state.page == PAGE_QUIZ) DrawQuizPage(memGfx);
        else DrawResultPage(memGfx);
        BitBlt(hdc, 0, 0, clientW, clientH, g_hdcMem, 0, 0, SRCCOPY);
        EndPaint(hwnd, &ps);
        return 0;
    }
    case WM_ERASEBKGND:
        return 1;
    case WM_DESTROY:
        KillTimer(hwnd, 1);
        DestroyEditControl();
        if (g_hdcMem) {
            DeleteObject(g_hbmMem);
            DeleteDC(g_hdcMem);
        }
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}
