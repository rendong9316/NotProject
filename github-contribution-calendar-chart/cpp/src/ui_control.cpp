#include "state.h"

#include "config.h"
#include "ui_draw.h"

#include <windows.h>
#include <windowsx.h>
#include <commctrl.h>
#include <algorithm>
#include <cmath>

namespace {

bool g_trackingMouse = false;
HBRUSH g_editLightBrush = nullptr;
HBRUSH g_editDarkBrush = nullptr;
HFONT g_editFont = nullptr;

void UpdateSearch() {
    const int length = GetWindowTextLengthW(g_hwndSearch);
    std::wstring query(static_cast<size_t>(length + 1), L'\0');
    if (length) GetWindowTextW(g_hwndSearch, &query[0], length + 1);
    query.resize(static_cast<size_t>(length));
    SetSearchQuery(query);
}

void UpdateSearchFont() {
    if (!g_hwndSearch) return;
    const int pixels = std::max(10, static_cast<int>(std::lround(13.0 * g_fontScale)));
    HFONT font = CreateFontW(-pixels, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                             OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                             DEFAULT_PITCH | FF_DONTCARE, FONT_FAMILY);
    if (!font) return;
    SendMessageW(g_hwndSearch, WM_SETFONT, reinterpret_cast<WPARAM>(font), TRUE);
    if (g_editFont) DeleteObject(g_editFont);
    g_editFont = font;
}

} // namespace

void RefreshSearchControlScale() {
    UpdateSearchFont();
    if (!g_hwndMain) return;
    RECT client = {};
    GetClientRect(g_hwndMain, &client);
    g_ui.Resize(client.right - client.left, client.bottom - client.top);
    g_ui.LayoutSearch(g_hwndSearch);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_CREATE: {
        g_editLightBrush = CreateSolidBrush(CLR_BG_PAGE);
        g_editDarkBrush = CreateSolidBrush(CLR_DARK_BG_PAGE);
        g_hwndSearch = CreateWindowExW(0, L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL,
                                       0, 0, 100, 24, hwnd, reinterpret_cast<HMENU>(IDC_SEARCH),
                                       reinterpret_cast<LPCREATESTRUCT>(lParam)->hInstance, nullptr);
        SendMessageW(g_hwndSearch, EM_SETCUEBANNER, TRUE, reinterpret_cast<LPARAM>(L"搜索名称或路径"));
        InitializeState(hwnd);
        RefreshSearchControlScale();
        return 0;
    }
    case WM_SIZE: {
        if (wParam == SIZE_MINIMIZED) return 0;
        g_ui.Resize(LOWORD(lParam), HIWORD(lParam));
        g_ui.LayoutSearch(g_hwndSearch);
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT: {
        PAINTSTRUCT paint = {};
        HDC dc = BeginPaint(hwnd, &paint);
        RECT client = {};
        GetClientRect(hwnd, &client);
        g_ui.Paint(dc, client.right, client.bottom);
        EndPaint(hwnd, &paint);
        return 0;
    }
    case WM_COMMAND:
        if (LOWORD(wParam) == IDC_SEARCH && HIWORD(wParam) == EN_CHANGE) { UpdateSearch(); return 0; }
        break;
    case WM_LBUTTONDOWN:
        SetFocus(hwnd);
        g_ui.MouseDown(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
        g_ui.Click(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
        return 0;
    case WM_LBUTTONUP:
        g_ui.MouseUp(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
        return 0;
    case WM_RBUTTONUP:
        g_ui.RightClick(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
        return 0;
    case WM_MOUSEMOVE: {
        if (!g_trackingMouse) {
            TRACKMOUSEEVENT tracking = {sizeof(TRACKMOUSEEVENT), TME_LEAVE, hwnd, 0};
            TrackMouseEvent(&tracking);
            g_trackingMouse = true;
        }
        g_ui.MouseMove(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
        return 0;
    }
    case WM_MOUSELEAVE:
        g_trackingMouse = false;
        g_ui.MouseLeave();
        return 0;
    case WM_MOUSEWHEEL: {
        POINT point = {GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
        ScreenToClient(hwnd, &point);
        // Ctrl+滚轮调节字体大小
        if (GetKeyState(VK_CONTROL) & 0x8000) {
            AdjustFontSize(GET_WHEEL_DELTA_WPARAM(wParam) > 0 ? 1 : -1);
            return 0;
        }
        g_ui.MouseWheel(point.x, point.y, GET_WHEEL_DELTA_WPARAM(wParam));
        return 0;
    }
    case WM_KEYDOWN:
        if (wParam == VK_ESCAPE) { g_ui.ClearDaySelection(); return 0; }
        if (wParam == VK_F5) { StartRefresh(); return 0; }
        if (wParam == VK_F6) { StartDiscovery(); return 0; }
        if (wParam == VK_LEFT) { ChangeYear(-1); return 0; }
        if (wParam == VK_RIGHT) { ChangeYear(1); return 0; }
        if (wParam == 'F' && (GetKeyState(VK_CONTROL) & 0x8000)) { SetFocus(g_hwndSearch); return 0; }
        if (wParam == 'D' && (GetKeyState(VK_CONTROL) & 0x8000)) { ToggleTheme(); return 0; }
        if (wParam == '+' && (GetKeyState(VK_CONTROL) & 0x8000)) { AdjustFontSize(1); return 0; }
        if (wParam == '-' && (GetKeyState(VK_CONTROL) & 0x8000)) { AdjustFontSize(-1); return 0; }
        break;
    case WM_CTLCOLORSTATIC:
    case WM_CTLCOLOREDIT:
        if (reinterpret_cast<HWND>(lParam) == g_hwndSearch) {
            SetTextColor(reinterpret_cast<HDC>(wParam), g_theme == Theme::Dark ? CLR_DARK_TEXT_PRIMARY : CLR_TEXT_PRIMARY);
            SetBkColor(reinterpret_cast<HDC>(wParam), g_theme == Theme::Dark ? CLR_DARK_BG_PAGE : CLR_BG_PAGE);
            return reinterpret_cast<LRESULT>(g_theme == Theme::Dark ? g_editDarkBrush : g_editLightBrush);
        }
        break;
    case WM_APP_OPERATION_DONE:
        HandleOperationDone(lParam);
        return 0;
    case WM_APP_PROGRESS:
        HandleOperationProgress(wParam, lParam);
        return 0;
    case WM_GETMINMAXINFO: {
        MINMAXINFO* info = reinterpret_cast<MINMAXINFO*>(lParam);
        info->ptMinTrackSize.x = 960;
        info->ptMinTrackSize.y = 680;
        return 0;
    }
    case WM_DESTROY:
        ShutdownState();
        if (g_editLightBrush) DeleteObject(g_editLightBrush);
        if (g_editDarkBrush) DeleteObject(g_editDarkBrush);
        if (g_editFont) DeleteObject(g_editFont);
        PostQuitMessage(0);
        return 0;
    default:
        break;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}
