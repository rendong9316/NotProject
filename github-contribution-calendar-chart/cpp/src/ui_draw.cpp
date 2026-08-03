#include "ui_draw.h"

#include "config.h"
#include "platform.h"
#include "state.h"
#include "cache_store.h"

#include <windows.h>
#include <wingdi.h>
#include <gdiplus.h>
#include <shellapi.h>
#include <algorithm>
#include <cmath>

using namespace Gdiplus;

// Global font scale factor (declared in state.cpp)
extern double g_fontScale;

UiDraw g_ui;

// Scale a value by the current font scale
inline int ScaleInt(int v) { return static_cast<int>(v * g_fontScale); }

namespace {

// Column resize: fixed ordering and default widths (pixels at fontScale=1.0)
constexpr int kColCount = 5;
// time | repo | message | author | hash
constexpr int kColDefaultWidths[kColCount] = {68, 140, 250, 100, 76};
constexpr int kColMinWidth = 40;
// Dividers to check for hover: 0..3 (between col0/1, 1/2, 2/3, 3/4)
constexpr int kColDividerCount = kColCount - 1;

COLORREF ThemeColor(COLORREF light, COLORREF dark) { return g_theme == Theme::Dark ? dark : light; }

Color ColorOf(COLORREF color, BYTE alpha = 255) {
    return Color(alpha, GetRValue(color), GetGValue(color), GetBValue(color));
}

void Fill(HDC dc, const RECT& rect, COLORREF color) {
    HBRUSH brush = CreateSolidBrush(color);
    FillRect(dc, &rect, brush);
    DeleteObject(brush);
}

// GDI+ filled rounded rectangle (no border).
void RoundRectFill(HDC dc, const RECT& rect, int radius, COLORREF fillColor) {
    Graphics graphics(dc);
    graphics.SetSmoothingMode(SmoothingModeHighQuality);
    GraphicsPath path;
    const int r2 = radius * 2;
    path.AddArc(rect.left, rect.top, r2, r2, 180, 90);
    path.AddArc(rect.right - r2, rect.top, r2, r2, 270, 90);
    path.AddArc(rect.right - r2, rect.bottom - r2, r2, r2, 0, 90);
    path.AddArc(rect.left, rect.bottom - r2, r2, r2, 90, 90);
    path.CloseFigure();
    SolidBrush fillBrush(ColorOf(fillColor));
    graphics.FillPath(&fillBrush, &path);
}

// GDI+ stroked rounded rectangle (no fill).
void RoundRectStroke(HDC dc, const RECT& rect, int radius, COLORREF borderColor) {
    Graphics graphics(dc);
    graphics.SetSmoothingMode(SmoothingModeHighQuality);
    GraphicsPath path;
    const int r2 = radius * 2;
    path.AddArc(rect.left, rect.top, r2, r2, 180, 90);
    path.AddArc(rect.right - r2, rect.top, r2, r2, 270, 90);
    path.AddArc(rect.right - r2, rect.bottom - r2, r2, r2, 0, 90);
    path.AddArc(rect.left, rect.bottom - r2, r2, r2, 90, 90);
    path.CloseFigure();
    Pen pen(ColorOf(borderColor), 1.0f);
    graphics.DrawPath(&pen, &path);
}

// GDI+ rounded rectangle with both fill and border.
void RoundRect(HDC dc, const RECT& rect, int radius, COLORREF fillColor, COLORREF borderColor) {
    RoundRectFill(dc, rect, radius, fillColor);
    RoundRectStroke(dc, rect, radius, borderColor);
}

void Line(HDC dc, int x1, int y1, int x2, int y2, COLORREF color) {
    HPEN pen = CreatePen(PS_SOLID, 1, color);
    HPEN old = static_cast<HPEN>(SelectObject(dc, pen));
    MoveToEx(dc, x1, y1, nullptr);
    LineTo(dc, x2, y2);
    SelectObject(dc, old);
    DeleteObject(pen);
}

HFONT MakeFont(int pixels, int weight = FW_NORMAL) {
    return CreateFontW(-pixels, 0, 0, 0, weight, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                       OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                       DEFAULT_PITCH | FF_DONTCARE, FONT_FAMILY);
}

inline int ScaleIntHelper(int v) { return static_cast<int>(v * g_fontScale); }

// Get the number of lines needed for text within a given width, using specified font size.
int GetTextLineCount(HDC dc, const std::wstring& text, int width, int fontSize) {
    if (width <= 0 || text.empty()) return 1;
    HFONT oldFont = MakeFont(fontSize, FW_NORMAL);
    HFONT font = static_cast<HFONT>(SelectObject(dc, oldFont));
    // Use DT_CALCRECT to compute needed rectangle size with word wrap
    RECT testRect = {0, 0, width, 1000};
    DrawTextW(dc, text.c_str(), static_cast<int>(text.size()), &testRect, DT_WORDBREAK | DT_CALCRECT | DT_SINGLELINE);
    SelectObject(dc, font);
    DeleteObject(oldFont);
    return (testRect.bottom + fontSize - 1) / fontSize;  // Approximate line count
}

// Overload: supports word wrap and multi-line drawing
void TextWrap(HDC dc, const std::wstring& value, RECT rect, int size, COLORREF color,
              int maxLines = 1, UINT flags = DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS, int weight = FW_NORMAL) {
    HFONT font = MakeFont(size, weight);
    HFONT old = static_cast<HFONT>(SelectObject(dc, font));
    SetTextColor(dc, color);
    SetBkMode(dc, TRANSPARENT);
    int drawFlags = flags;
    if (maxLines > 1) {
        drawFlags |= DT_WORDBREAK;
    }
    DrawTextW(dc, value.c_str(), static_cast<int>(value.size()), &rect, drawFlags);
    SelectObject(dc, old);
    DeleteObject(font);
}

// Single-line text drawing helper
void Text(HDC dc, const std::wstring& value, RECT rect, int size, COLORREF color,
          UINT flags = DT_LEFT | DT_VCENTER | DT_SINGLELINE, int weight = FW_NORMAL) {
    HFONT font = MakeFont(size, weight);
    HFONT old = static_cast<HFONT>(SelectObject(dc, font));
    SetTextColor(dc, color);
    SetBkMode(dc, TRANSPARENT);
    DrawTextW(dc, value.c_str(), static_cast<int>(value.size()), &rect, flags);
    SelectObject(dc, old);
    DeleteObject(font);
}

bool Contains(const RECT& rect, int x, int y) { return x >= rect.left && x < rect.right && y >= rect.top && y <= rect.bottom; }

bool CopyTextToClipboard(HWND owner, const std::wstring& value) {
    if (value.empty() || !OpenClipboard(owner)) return false;
    EmptyClipboard();
    const SIZE_T bytes = (value.size() + 1) * sizeof(wchar_t);
    HGLOBAL memory = GlobalAlloc(GMEM_MOVEABLE, bytes);
    if (!memory) {
        CloseClipboard();
        return false;
    }
    void* destination = GlobalLock(memory);
    if (!destination) {
        GlobalFree(memory);
        CloseClipboard();
        return false;
    }
    CopyMemory(destination, value.c_str(), bytes);
    GlobalUnlock(memory);
    const bool copied = SetClipboardData(CF_UNICODETEXT, memory) != nullptr;
    if (!copied) GlobalFree(memory);
    CloseClipboard();
    return copied;
}

void Button(HDC dc, const RECT& rect, const std::wstring& label, bool primary, bool disabled = false) {
    const COLORREF border = ThemeColor(CLR_BORDER, CLR_DARK_BORDER);
    COLORREF background = primary ? ThemeColor(CLR_ACCENT, CLR_DARK_ACCENT)
                                  : ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE);
    COLORREF text = primary ? RGB(255,255,255) : ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY);
    if (disabled) {
        background = ThemeColor(CLR_BG_HOVER, CLR_DARK_BG_HOVER);
        text = ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC);
    }
    Fill(dc, rect, background);
    HBRUSH outline = CreateSolidBrush(primary ? background : border);
    FrameRect(dc, &rect, outline);
    DeleteObject(outline);
    Text(dc, label, rect, ScaleIntHelper(13), text, DT_CENTER | DT_VCENTER | DT_SINGLELINE, primary ? FW_SEMIBOLD : FW_NORMAL);
}

void Checkbox(HDC dc, int x, int y, bool checked, bool enabled = true) {
    RECT box = {x, y, x + 16, y + 16};
    Fill(dc, box, checked ? ThemeColor(CLR_ACCENT, CLR_DARK_ACCENT) : ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE));
    HBRUSH border = CreateSolidBrush(ThemeColor(CLR_BORDER, CLR_DARK_BORDER));
    FrameRect(dc, &box, border);
    DeleteObject(border);
    if (checked) {
        HPEN pen = CreatePen(PS_SOLID, 2, enabled ? RGB(255,255,255) : CLR_TEXT_TERTIARY);
        HPEN old = static_cast<HPEN>(SelectObject(dc, pen));
        MoveToEx(dc, x + 3, y + 8, nullptr);
        LineTo(dc, x + 7, y + 12);
        LineTo(dc, x + 14, y + 4);
        SelectObject(dc, old);
        DeleteObject(pen);
    }
}

COLORREF HeatColor(int count, int maximum, bool inYear) {
    if (!inYear) return ThemeColor(CLR_BG_PAGE, CLR_DARK_BG_PAGE);
    if (count <= 0) return ThemeColor(CLR_GREEN_0, CLR_DARK_GREEN_0);
    const int level = maximum <= 4 ? std::min(4, count)
                                   : std::max(1, std::min(4, static_cast<int>(std::ceil(count * 4.0 / maximum))));
    static const COLORREF light[] = {CLR_GREEN_0, CLR_GREEN_1, CLR_GREEN_2, CLR_GREEN_3, CLR_GREEN_4};
    static const COLORREF dark[] = {CLR_DARK_GREEN_0, CLR_DARK_GREEN_1, CLR_DARK_GREEN_2, CLR_DARK_GREEN_3, CLR_DARK_GREEN_4};
    return g_theme == Theme::Dark ? dark[level] : light[level];
}

std::wstring MonthLabel(const std::wstring& date) {
    if (date.size() < 7) return L"";
    const int month = _wtoi(date.substr(5, 2).c_str());
    return FormatInteger(month) + L"月";
}

// Compute absolute X positions for column edges given content area bounds.
// out[0..4] = left edges of each column, out[5] = right edge of last column.
void GetColumnRects(const RECT& panel, int inset, const std::vector<int>& colWidths, int* out) {
    const int contentLeft = panel.left + inset;
    const int contentRight = panel.right - inset;
    const int contentWidth = std::max(1, contentRight - contentLeft);

    int widths[kColCount];
    // First 4 columns: at least min width, preserve relative proportions if needed
    int minFixedW = 0;
    for (int i = 0; i < kColCount - 1; ++i) {
        widths[i] = std::max(kColMinWidth, colWidths[i]);
        minFixedW += widths[i];
    }

    // If first 4 cols exceed panel, proportionally scale them down
    if (minFixedW >= contentWidth) {
        double scale = static_cast<double>(contentWidth) / minFixedW;
        for (int i = 0; i < kColCount - 1; ++i) {
            widths[i] = std::max(kColMinWidth, static_cast<int>(widths[i] * scale));
        }
        minFixedW = 0;
        for (int i = 0; i < kColCount - 1; ++i) minFixedW += widths[i];
    }

    // Last column takes remaining space
    widths[kColCount - 1] = contentWidth - minFixedW;

    int x = contentLeft;
    for (int i = 0; i < kColCount; ++i) {
        out[i] = x;
        x += widths[i];
    }
    out[kColCount] = contentRight;
}

} // namespace

// Initialize colWidths_ from defaults (or config), scaled to current font scale.
void UiDraw::InitColumnWidths() {
    colWidths_.resize(kColCount);
    if (g_config.columnWidths.size() == kColCount) {
        for (int i = 0; i < kColCount; ++i)
            colWidths_[i] = static_cast<int>(g_config.columnWidths[i] * g_fontScale);
    } else {
        for (int i = 0; i < kColCount; ++i)
            colWidths_[i] = static_cast<int>(kColDefaultWidths[i] * g_fontScale);
    }
}

void UiDraw::ComputeLayout(int width, int height) {
    width_ = width;
    height_ = height;

    // All layout dimensions are scaled by g_fontScale
    discoverRect_ = {width - ScaleIntHelper(456), ScaleIntHelper(12), width - ScaleIntHelper(326), ScaleIntHelper(46)};
    refreshRect_ = {width - ScaleIntHelper(316), ScaleIntHelper(12), width - ScaleIntHelper(196), ScaleIntHelper(46)};
    themeRect_ = {width - ScaleIntHelper(178), ScaleIntHelper(12), width - ScaleIntHelper(28), ScaleIntHelper(46)};

    searchRect_ = {ScaleIntHelper(14), ScaleIntHelper(TOPBAR_H + 48), ScaleIntHelper(SIDEBAR_W) - ScaleIntHelper(14), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(82)};
    selectAllRect_ = {0, ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(92), ScaleIntHelper(SIDEBAR_W), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(128)};
    repoListRect_ = {0, ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(132), ScaleIntHelper(SIDEBAR_W), height - ScaleIntHelper(42)};

    yearPreviousRect_ = {ScaleIntHelper(SIDEBAR_W) + ScaleIntHelper(CONTENT_PAD), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(20),
                         ScaleIntHelper(SIDEBAR_W) + ScaleIntHelper(CONTENT_PAD) + ScaleIntHelper(34), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(52)};
    yearNextRect_ = {ScaleIntHelper(SIDEBAR_W) + ScaleIntHelper(CONTENT_PAD) + ScaleIntHelper(122), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(20),
                     ScaleIntHelper(SIDEBAR_W) + ScaleIntHelper(CONTENT_PAD) + ScaleIntHelper(156), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(52)};

    const int contentLeft = ScaleIntHelper(SIDEBAR_W) + ScaleIntHelper(CONTENT_PAD);
    const int contentRight = width - ScaleIntHelper(CONTENT_PAD);
    const int available = std::max(ScaleIntHelper(300), contentRight - contentLeft - ScaleIntHelper(58));
    const int weeks = std::max(1, static_cast<int>(g_contributionData.days.size() / 7));
    dayGap_ = available < ScaleIntHelper(700) ? ScaleIntHelper(2) : ScaleIntHelper(3);
    daySize_ = std::max(ScaleIntHelper(7), std::min(ScaleIntHelper(13), (available / weeks) - dayGap_));

    const int calendarWidth = ScaleIntHelper(54) + weeks * (daySize_ + dayGap_) + ScaleIntHelper(16);
    const int calendarHeight = ScaleIntHelper(40) + 7 * (daySize_ + dayGap_) + ScaleIntHelper(42);
    calendarRect_ = {contentLeft, ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(112), std::min(contentRight, contentLeft + calendarWidth),
                     ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(112) + calendarHeight};
    // Detail panel: below calendar, above status bar
    const int panelTop = calendarRect_.bottom + ScaleIntHelper(12);
    const int panelHeight = std::max(ScaleIntHelper(0), height_ - ScaleIntHelper(42) - panelTop - ScaleIntHelper(8));
    detailPanelRect_ = {contentLeft, panelTop, contentRight, panelTop + panelHeight};
    if (selectedDay_ >= 0 && panelHeight < ScaleIntHelper(140)) {
        detailPanelRect_.top = calendarRect_.top;
        detailPanelRect_.bottom = height_ - ScaleIntHelper(50);
    }
}

void UiDraw::Resize(int width, int height) { ComputeLayout(width, height); }

void UiDraw::Paint(HDC target, int width, int height) {
    ComputeLayout(width, height);
    HDC buffer = CreateCompatibleDC(target);
    HBITMAP bitmap = CreateCompatibleBitmap(target, width, height);
    HBITMAP previous = static_cast<HBITMAP>(SelectObject(buffer, bitmap));
    RECT all = {0, 0, width, height};
    Fill(buffer, all, ThemeColor(CLR_BG_PAGE, CLR_DARK_BG_PAGE));
    DrawTopbar(buffer);
    DrawSidebar(buffer);
    DrawContent(buffer);
    DrawStatusbar(buffer);
    DrawTooltip(buffer);
    BitBlt(target, 0, 0, width, height, buffer, 0, 0, SRCCOPY);
    SelectObject(buffer, previous);
    DeleteObject(bitmap);
    DeleteDC(buffer);
}

void UiDraw::LayoutSearch(HWND edit) const {
    if (!edit) return;
    SetWindowPos(edit, nullptr, searchRect_.left + ScaleIntHelper(8), searchRect_.top + ScaleIntHelper(5),
                 searchRect_.right - searchRect_.left - ScaleIntHelper(16), searchRect_.bottom - searchRect_.top - ScaleIntHelper(10),
                 SWP_NOZORDER | SWP_NOACTIVATE);
}

void UiDraw::DrawTopbar(HDC dc) {
    RECT rect = {0, 0, width_, ScaleIntHelper(TOPBAR_H)};
    Fill(dc, rect, ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE));
    Line(dc, 0, ScaleIntHelper(TOPBAR_H) - 1, width_, ScaleIntHelper(TOPBAR_H) - 1, ThemeColor(CLR_BORDER, CLR_DARK_BORDER));

    int titleHeight = ScaleIntHelper(32);
    int subtitleHeight = ScaleIntHelper(32);
    RECT title = {ScaleIntHelper(20), ScaleIntHelper(6), width_ - ScaleIntHelper(20), ScaleIntHelper(6) + titleHeight};
    Text(dc, L"Git Local", title, ScaleIntHelper(17), ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY),
         DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS, FW_SEMIBOLD);

    RECT subtitle = {ScaleIntHelper(20), ScaleIntHelper(titleHeight + 8), width_ - ScaleIntHelper(20), ScaleIntHelper(6) + titleHeight + subtitleHeight};
    Text(dc, L"原生 C++ · 本机数据 · 增量刷新", subtitle, ScaleIntHelper(11), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC));

    Button(dc, discoverRect_, L"发现新项目", false, g_loading);
    Button(dc, refreshRect_, g_loading && g_operationKind == OperationKind::Refresh ? L"正在刷新…" : L"刷新提交", true, g_loading);
    Button(dc, themeRect_, g_theme == Theme::Dark ? L"切换亮色" : L"切换暗色", false);
}

void UiDraw::DrawSidebar(HDC dc) {
    RECT sidebar = {0, ScaleIntHelper(TOPBAR_H), ScaleIntHelper(SIDEBAR_W), height_ - ScaleIntHelper(42)};
    Fill(dc, sidebar, ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE));
    Line(dc, ScaleIntHelper(SIDEBAR_W) - 1, ScaleIntHelper(TOPBAR_H), ScaleIntHelper(SIDEBAR_W), height_, ThemeColor(CLR_BORDER, CLR_DARK_BORDER));

    RECT heading = {ScaleIntHelper(14), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(10), ScaleIntHelper(SIDEBAR_W) - ScaleIntHelper(12), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(40)};
    Text(dc, L"项目  " + FormatInteger(static_cast<int>(g_repos.size())), heading, ScaleIntHelper(14),
         ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY), DT_LEFT | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);

    Fill(dc, searchRect_, ThemeColor(CLR_BG_PAGE, CLR_DARK_BG_PAGE));
    HBRUSH searchBorder = CreateSolidBrush(ThemeColor(CLR_BORDER, CLR_DARK_BORDER));
    FrameRect(dc, &searchRect_, searchBorder);
    DeleteObject(searchBorder);

    const std::vector<size_t> visible = VisibleRepositoryIndices();
    int selectedVisible = 0;
    for (size_t index : visible) if (g_selected[g_repos[index].id]) ++selectedVisible;

    Checkbox(dc, 15, selectAllRect_.top + ScaleIntHelper(10), !visible.empty() && selectedVisible == static_cast<int>(visible.size()));
    RECT allText = {ScaleIntHelper(42), selectAllRect_.top, ScaleIntHelper(SIDEBAR_W) - ScaleIntHelper(12), selectAllRect_.bottom};
    Text(dc, L"全部可见项目  " + FormatInteger(selectedVisible) + L"/" + FormatInteger(static_cast<int>(visible.size())),
         allText, ScaleIntHelper(12), ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY),
         DT_LEFT | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);

    Line(dc, ScaleIntHelper(12), selectAllRect_.bottom, ScaleIntHelper(SIDEBAR_W) - ScaleIntHelper(12), selectAllRect_.bottom, ThemeColor(CLR_BORDER, CLR_DARK_BORDER));

    const int rowHeight = ScaleIntHelper(40);
    const int capacity = std::max(0, static_cast<int>((repoListRect_.bottom - repoListRect_.top) / rowHeight));
    const int maxScroll = std::max(0, static_cast<int>(visible.size()) - capacity);
    g_repoScroll = std::max(0, std::min(g_repoScroll, maxScroll));

    for (int row = 0; row < capacity && g_repoScroll + row < static_cast<int>(visible.size()); ++row) {
        const Repository& repository = g_repos[visible[g_repoScroll + row]];
        const int y = repoListRect_.top + row * rowHeight;
        RECT rowRect = {0, y, ScaleIntHelper(SIDEBAR_W) - 1, y + rowHeight};
        if (!repository.available) Fill(dc, rowRect, ThemeColor(RGB(255,248,247), RGB(42,25,29)));
        Checkbox(dc, 14, y + ScaleIntHelper(12), g_selected[repository.id], repository.available);
        RECT name = {ScaleIntHelper(42), y + ScaleIntHelper(3), ScaleIntHelper(SIDEBAR_W) - ScaleIntHelper(24), y + ScaleIntHelper(23)};
        Text(dc, repository.name, name, ScaleIntHelper(12),
             !repository.available ? ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC)
                                   : repository.yearTotal == 0 ? ThemeColor(RGB(158,158,158), RGB(110,110,110))
                                                             : ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY));
        RECT path = {ScaleIntHelper(42), y + ScaleIntHelper(21), ScaleIntHelper(SIDEBAR_W) - ScaleIntHelper(18), y + ScaleIntHelper(38)};
        Text(dc, repository.available ? repository.path : L"不可用 · " + repository.error, path, ScaleIntHelper(10),
             !repository.available ? ThemeColor(CLR_DANGER_TEXT, RGB(255,123,114))
                                   : repository.yearTotal == 0 ? ThemeColor(RGB(180,180,180), RGB(85,85,85))
                                                               : ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC));
    }

    if (visible.empty()) {
        RECT empty = {ScaleIntHelper(20), repoListRect_.top + ScaleIntHelper(20), ScaleIntHelper(SIDEBAR_W) - ScaleIntHelper(20), repoListRect_.top + ScaleIntHelper(90)};
        Text(dc, g_query.empty() ? L"尚未发现 Git 项目" : L"没有匹配的项目", empty, ScaleIntHelper(12),
             ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC), DT_CENTER | DT_VCENTER | DT_WORDBREAK);
    }
}

void UiDraw::DrawContent(HDC dc) {
    const COLORREF primary = ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY);

    Button(dc, yearPreviousRect_, L"<", false);
    Button(dc, yearNextRect_, L">", false);

    RECT year = {yearPreviousRect_.right + ScaleIntHelper(4), yearPreviousRect_.top, yearNextRect_.left - ScaleIntHelper(4), yearNextRect_.bottom};
    Text(dc, FormatInteger(g_year), year, ScaleIntHelper(18), primary, DT_CENTER | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);

    const int metricsLeft = yearNextRect_.right + ScaleIntHelper(32);
    const int metricsTop = ScaleIntHelper(TOPBAR_H + 12);
    const int metricWidth = ScaleIntHelper(145);
    const std::wstring values[] = {FormatInteger(g_contributionData.total), FormatInteger(g_contributionData.activeDays),
                                   FormatInteger(SelectedRepositoryCount())};
    const wchar_t* labels[] = {L"提交", L"活跃日", L"已选项目"};
    for (int index = 0; index < 3; ++index) {
        RECT value = {metricsLeft + index * metricWidth, metricsTop, metricsLeft + (index + 1) * metricWidth - ScaleIntHelper(12), metricsTop + ScaleIntHelper(30)};
        RECT label = {value.left, metricsTop + ScaleIntHelper(28), value.right, metricsTop + ScaleIntHelper(50)};
        Text(dc, values[index], value, ScaleIntHelper(21), primary, DT_LEFT | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);
        Text(dc, labels[index], label, ScaleIntHelper(11), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC));
    }

    if (!g_error.empty()) {
        RECT error = {ScaleIntHelper(SIDEBAR_W) + ScaleIntHelper(CONTENT_PAD), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(68), width_ - ScaleIntHelper(CONTENT_PAD), ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(102)};
        Fill(dc, error, ThemeColor(CLR_DANGER_BG, RGB(61,19,24)));
        RECT text = {error.left + ScaleIntHelper(10), error.top, error.right - ScaleIntHelper(10), error.bottom};
        Text(dc, g_error, text, ScaleIntHelper(11), ThemeColor(CLR_DANGER_TEXT, RGB(255,123,114)));
    }

    DrawCalendar(dc);
    if (selectedDay_ >= 0) DrawDayDetailPanel(dc);
    else DrawRanking(dc);
}

void UiDraw::DrawCalendar(HDC dc) {
    Fill(dc, calendarRect_, ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE));
    HBRUSH border = CreateSolidBrush(ThemeColor(CLR_BORDER, CLR_DARK_BORDER));
    FrameRect(dc, &calendarRect_, border);
    DeleteObject(border);

    RECT title = {calendarRect_.left + ScaleIntHelper(16), calendarRect_.top + ScaleIntHelper(4), calendarRect_.right - ScaleIntHelper(16), calendarRect_.top + ScaleIntHelper(29)};
    Text(dc, FormatInteger(g_year) + L" 年提交分布", title, ScaleIntHelper(13),
         ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY), DT_LEFT | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);

    if (g_contributionData.days.empty()) return;

    const int gridX = calendarRect_.left + ScaleIntHelper(45);
    const int gridY = calendarRect_.top + ScaleIntHelper(34);
    const int stride = daySize_ + dayGap_;
    const int weeks = static_cast<int>(g_contributionData.days.size() / 7);

    int maximum = 0;
    for (const DayEntry& day : g_contributionData.days) if (day.inYear) maximum = std::max(maximum, day.count);

    const wchar_t* weekdays[] = {L"日", L"一", L"二", L"三", L"四", L"五", L"六"};
    for (int day = 0; day < 7; ++day) {
        RECT label = {calendarRect_.left + ScaleIntHelper(10), gridY + day * stride - ScaleIntHelper(2), gridX - ScaleIntHelper(8), gridY + day * stride + daySize_ + ScaleIntHelper(2)};
        if (day == 1 || day == 3 || day == 5)
            Text(dc, weekdays[day], label, ScaleIntHelper(10), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC), DT_RIGHT | DT_VCENTER | DT_SINGLELINE);
    }

    int lastMonth = 0;
    for (int week = 0; week < weeks; ++week) {
        for (int day = 0; day < 7; ++day) {
            const int index = week * 7 + day;
            const DayEntry& entry = g_contributionData.days[index];
            RECT square = {gridX + week * stride, gridY + day * stride,
                           gridX + week * stride + daySize_, gridY + day * stride + daySize_};
            RoundRectFill(dc, square, daySize_ / 3, HeatColor(entry.count, maximum, entry.inYear));
            if (index == selectedDay_)
                RoundRectStroke(dc, square, daySize_ / 3, ThemeColor(CLR_ACCENT, CLR_DARK_ACCENT));
            if (index == hoveredDay_ && entry.inYear)
                RoundRectStroke(dc, square, daySize_ / 3, ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY));
            if (entry.inYear && entry.date.size() >= 7) {
                const int month = _wtoi(entry.date.substr(5, 2).c_str());
                if (month != lastMonth && _wtoi(entry.date.substr(8, 2).c_str()) <= 7) {
                    RECT monthRect = {square.left - ScaleIntHelper(2), calendarRect_.top + ScaleIntHelper(18),
                                      square.left + ScaleIntHelper(42), calendarRect_.top + ScaleIntHelper(32)};
                    Text(dc, MonthLabel(entry.date), monthRect, ScaleIntHelper(10), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC),
                         DT_LEFT | DT_BOTTOM | DT_SINGLELINE);
                    lastMonth = month;
                }
            }
        }
    }

    const int legendY = gridY + 7 * stride + ScaleIntHelper(12);
    RECT less = {calendarRect_.right - ScaleIntHelper(190), legendY, calendarRect_.right - ScaleIntHelper(158), legendY + ScaleIntHelper(18)};
    Text(dc, L"少", less, ScaleIntHelper(10), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC), DT_RIGHT | DT_VCENTER | DT_SINGLELINE);
    for (int level = 0; level < 5; ++level) {
        RECT square = {calendarRect_.right - ScaleIntHelper(150) + level * ScaleIntHelper(19), legendY + ScaleIntHelper(2),
                       calendarRect_.right - ScaleIntHelper(138) + level * ScaleIntHelper(19), legendY + ScaleIntHelper(14)};
        RoundRectFill(dc, square, 2, HeatColor(level, 4, true));
    }
    RECT more = {calendarRect_.right - ScaleIntHelper(50), legendY, calendarRect_.right - ScaleIntHelper(14), legendY + ScaleIntHelper(18)};
    Text(dc, L"多", more, ScaleIntHelper(10), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC));
}

void UiDraw::DrawRanking(HDC dc) {
    const int top = calendarRect_.bottom + ScaleIntHelper(22);
    if (top > height_ - ScaleIntHelper(88)) return;

    RECT title = {calendarRect_.left, top, calendarRect_.right, top + ScaleIntHelper(28)};
    Text(dc, L"项目提交排行", title, ScaleIntHelper(14), ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY),
         DT_LEFT | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);

    const int availableHeight = height_ - ScaleIntHelper(48) - (top + ScaleIntHelper(30));
    const int rows = std::min(6, std::min(static_cast<int>(g_contributionData.repoStats.size()), availableHeight / ScaleIntHelper(30)));
    const int maximum = rows ? g_contributionData.repoStats[0].count : 1;

    for (int index = 0; index < rows; ++index) {
        const ContributionData::RepoStat& stat = g_contributionData.repoStats[index];
        const int y = top + ScaleIntHelper(31) + index * ScaleIntHelper(30);
        RECT name = {calendarRect_.left, y, calendarRect_.left + ScaleIntHelper(180), y + ScaleIntHelper(24)};
        Text(dc, stat.name, name, ScaleIntHelper(11), ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY));
        RECT track = {calendarRect_.left + ScaleIntHelper(190), y + ScaleIntHelper(8), calendarRect_.right - ScaleIntHelper(62), y + ScaleIntHelper(16)};
        Fill(dc, track, ThemeColor(CLR_GREEN_0, CLR_DARK_BG_HOVER));
        RECT value = track;
        value.right = value.left + static_cast<int>((track.right - track.left) * (stat.count / static_cast<double>(maximum)));
        Fill(dc, value, ThemeColor(CLR_GREEN_3, CLR_DARK_GREEN_3));
        RECT count = {calendarRect_.right - ScaleIntHelper(55), y, calendarRect_.right, y + ScaleIntHelper(24)};
        Text(dc, FormatInteger(stat.count), count, ScaleIntHelper(11), ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY),
             DT_RIGHT | DT_VCENTER | DT_SINGLELINE);
    }

    if (!rows) {
        RECT empty = {calendarRect_.left, top + ScaleIntHelper(30), calendarRect_.right, top + ScaleIntHelper(70)};
        Text(dc, L"当前筛选范围内没有提交", empty, ScaleIntHelper(11), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC));
    }
}

void UiDraw::DrawStatusbar(HDC dc) {
    RECT bar = {0, height_ - ScaleIntHelper(42), width_, height_};
    Fill(dc, bar, ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE));
    Line(dc, 0, bar.top, width_, bar.top, ThemeColor(CLR_BORDER, CLR_DARK_BORDER));

    int statusLeftPadding = ScaleIntHelper(16);
    int statusRightPadding = ScaleIntHelper(360);
    RECT status = {statusLeftPadding, bar.top, width_ - statusRightPadding, bar.bottom};
    std::wstring text = g_status.empty() ? (g_repos.empty() ? L"点击\"发现新项目\"开始扫描" : L"数据已就绪") : g_status;
    Text(dc, text, status, ScaleIntHelper(11), g_error.empty() ? ThemeColor(CLR_TEXT_SECONDARY, CLR_DARK_TEXT_SEC)
                                                     : ThemeColor(CLR_DANGER_TEXT, RGB(255,123,114)));

    int detailsMinWidth = ScaleIntHelper(360);
    RECT details = {width_ - detailsMinWidth, bar.top, width_ - ScaleIntHelper(16), bar.bottom};
    Text(dc, FormatInteger(static_cast<int>(g_repos.size())) + L" 个项目 · " + g_gitVersion, details, ScaleIntHelper(10),
         ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC), DT_RIGHT | DT_VCENTER | DT_SINGLELINE);
}

void UiDraw::DrawTooltip(HDC dc) {
    if (hoveredDay_ < 0 || hoveredDay_ >= static_cast<int>(g_contributionData.days.size())) return;
    const DayEntry& day = g_contributionData.days[hoveredDay_];
    if (!day.inYear) return;

    int lines = std::min(4, static_cast<int>(day.details.size()));
    const int tooltipWidth = ScaleIntHelper(250);
    const int tooltipHeight = ScaleIntHelper(52) + lines * ScaleIntHelper(19);
    int x = std::min(mouseX_ + ScaleIntHelper(14), width_ - tooltipWidth - ScaleIntHelper(8));
    int y = mouseY_ - tooltipHeight - ScaleIntHelper(10);
    if (y < ScaleIntHelper(TOPBAR_H) + ScaleIntHelper(4)) y = mouseY_ + ScaleIntHelper(18);

    RECT box = {x, y, x + tooltipWidth, y + tooltipHeight};
    constexpr int tooltipRadius = 6;
    RoundRect(dc, box, tooltipRadius, ThemeColor(RGB(36,41,47), RGB(230,237,243)),
              ThemeColor(RGB(36,41,47), RGB(230,237,243)));

    const COLORREF foreground = ThemeColor(RGB(255,255,255), RGB(31,35,40));
    RECT headline = {x + ScaleIntHelper(10), y + ScaleIntHelper(5), box.right - ScaleIntHelper(10), y + ScaleIntHelper(29)};
    Text(dc, day.date + L" · " + FormatInteger(day.count) + L" 次提交", headline, ScaleIntHelper(12), foreground,
         DT_LEFT | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);

    if (day.details.empty()) {
        RECT empty = {x + ScaleIntHelper(10), y + ScaleIntHelper(28), box.right - ScaleIntHelper(10), box.bottom - ScaleIntHelper(6)};
        Text(dc, L"当天没有提交", empty, ScaleIntHelper(10), foreground);
    } else {
        for (int index = 0; index < lines; ++index) {
            RECT detail = {x + ScaleIntHelper(10), y + ScaleIntHelper(29) + index * ScaleIntHelper(19), box.right - ScaleIntHelper(10), y + ScaleIntHelper(48) + index * ScaleIntHelper(19)};
            Text(dc, day.details[index].repoName + L"  " + FormatInteger(day.details[index].count), detail, ScaleIntHelper(10), foreground);
        }
    }
}

int UiDraw::RepositoryAt(int x, int y) const {
    if (!Contains(repoListRect_, x, y)) return -1;
    const int row = (y - repoListRect_.top) / ScaleIntHelper(40);
    const std::vector<size_t> visible = VisibleRepositoryIndices();
    const int visibleIndex = g_repoScroll + row;
    return visibleIndex >= 0 && visibleIndex < static_cast<int>(visible.size()) ? static_cast<int>(visible[visibleIndex]) : -1;
}

int UiDraw::VisibleCommitRows() const {
    const int listTop = detailPanelRect_.top + ScaleIntHelper(62);
    const int listHeight = std::max(0, static_cast<int>(detailPanelRect_.bottom) - listTop);
    return std::max(1, listHeight / std::max(1, ScaleIntHelper(40)));
}

int UiDraw::CommitAt(int x, int y) const {
    if (selectedDay_ < 0 || selectedDay_ >= static_cast<int>(g_contributionData.days.size()) ||
        !Contains(detailPanelRect_, x, y)) return -1;
    const int listTop = detailPanelRect_.top + ScaleIntHelper(62);
    const int rowHeight = ScaleIntHelper(40);
    if (y < listTop || rowHeight <= 0) return -1;
    const int visibleRow = (y - listTop) / rowHeight;
    if (visibleRow < 0 || visibleRow >= VisibleCommitRows()) return -1;
    const int index = commitScroll_ + visibleRow;
    const int total = static_cast<int>(g_contributionData.days[selectedDay_].commits.size());
    return index >= 0 && index < total ? index : -1;
}

bool UiDraw::Click(int x, int y) {
    if (Contains(themeRect_, x, y)) { ToggleTheme(); return true; }
    if (Contains(refreshRect_, x, y)) { StartRefresh(); return true; }
    if (Contains(discoverRect_, x, y)) { StartDiscovery(); return true; }
    if (Contains(yearPreviousRect_, x, y)) { ChangeYear(-1); return true; }
    if (Contains(yearNextRect_, x, y)) { ChangeYear(1); return true; }
    if (Contains(selectAllRect_, x, y)) {
        const std::vector<size_t> visible = VisibleRepositoryIndices();
        bool allSelected = !visible.empty();
        for (size_t index : visible) if (!g_selected[g_repos[index].id]) { allSelected = false; break; }
        SelectAllVisible(!allSelected);
        return true;
    }
    const int repository = RepositoryAt(x, y);
    if (repository >= 0) { ToggleRepository(g_repos[repository].id); return true; }
    // Column resize start
    if (selectedDay_ >= 0 && colDragDivider_ >= 0 && Contains(detailPanelRect_, x, y)) {
        MouseDown(x, y);
        return true;
    }
    if (selectedDay_ >= 0 && Contains(detailPanelRect_, x, y)) {
        const RECT closeRect = {detailPanelRect_.right - ScaleIntHelper(36), detailPanelRect_.top,
                                detailPanelRect_.right, detailPanelRect_.top + ScaleIntHelper(36)};
        if (Contains(closeRect, x, y)) ClearDaySelection();
        return true;
    }
    // Handle calendar day click for detail panel
    if (Contains(calendarRect_, x, y) && !g_contributionData.days.empty()) {
        const int gridX = calendarRect_.left + ScaleIntHelper(45);
        const int gridY = calendarRect_.top + ScaleIntHelper(34);
        const int stride = daySize_ + dayGap_;
        const int week = (x - gridX) / stride;
        const int dayOfWeek = (y - gridY) / stride;
        if (x >= gridX && y >= gridY && week >= 0 && dayOfWeek >= 0 && dayOfWeek < 7) {
            const int index = week * 7 + dayOfWeek;
            if (index < static_cast<int>(g_contributionData.days.size())) {
                const DayEntry& entry = g_contributionData.days[index];
                const int localX = (x - gridX) % stride;
                const int localY = (y - gridY) % stride;
                if (localX < daySize_ && localY < daySize_ && entry.count > 0) {
                    SelectDay(index); return true;
                }
            }
        }
    }
    // Close detail panel when clicking outside
    if (selectedDay_ >= 0 && !Contains(detailPanelRect_, x, y) && !Contains(calendarRect_, x, y)) {
        ClearDaySelection(); return true;
    }
    return false;
}

bool UiDraw::RightClick(int x, int y) {
    const int commitIndex = CommitAt(x, y);
    if (commitIndex >= 0) {
        const DayEntry::CommitEntry& commit = g_contributionData.days[selectedDay_].commits[commitIndex];
        HMENU menu = CreatePopupMenu();
        if (!menu) return false;
        constexpr UINT OPEN_REPOSITORY = 1;
        constexpr UINT COPY_HASH = 2;
        AppendMenuW(menu, MF_STRING | (commit.repoPath.empty() ? MF_GRAYED : 0), OPEN_REPOSITORY, L"打开项目目录");
        AppendMenuW(menu, MF_STRING | (commit.hash.empty() ? MF_GRAYED : 0), COPY_HASH, L"复制提交哈希");
        POINT point = {x, y};
        ClientToScreen(g_hwndMain, &point);
        const UINT command = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON,
                                             point.x, point.y, 0, g_hwndMain, nullptr);
        DestroyMenu(menu);
        if (command == OPEN_REPOSITORY)
            ShellExecuteW(g_hwndMain, L"open", commit.repoPath.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        else if (command == COPY_HASH)
            CopyTextToClipboard(g_hwndMain, commit.hash);
        return true;
    }
    const int repository = RepositoryAt(x, y);
    if (repository < 0) return false;
    ShellExecuteW(g_hwndMain, L"open", g_repos[repository].path.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
    return true;
}

void UiDraw::MouseMove(int x, int y) {
    mouseX_ = x;
    mouseY_ = y;
    int next = -1;
    if (Contains(calendarRect_, x, y) &&
        !(selectedDay_ >= 0 && Contains(detailPanelRect_, x, y)) &&
        !g_contributionData.days.empty()) {
        const int gridX = calendarRect_.left + ScaleIntHelper(45);
        const int gridY = calendarRect_.top + ScaleIntHelper(34);
        const int stride = daySize_ + dayGap_;
        const int week = (x - gridX) / stride;
        const int day = (y - gridY) / stride;
        if (x >= gridX && y >= gridY && week >= 0 && day >= 0 && day < 7) {
            const int index = week * 7 + day;
            const int localX = (x - gridX) % stride;
            const int localY = (y - gridY) % stride;
            if (index < static_cast<int>(g_contributionData.days.size()) && localX < daySize_ && localY < daySize_) {
                next = index;
            }
        }
    }

    // Column resize drag
    if (colResizeState_ == ColResizeState::Dragging && colResizeColumn_ >= 0) {
        const int delta = x - colResizeEndX_;
        const int col = colResizeColumn_;
        const int minW = kColMinWidth;
        // Constrain: left col grows at most by remaining right width, right col shrinks at most by left width
        int adj = delta;
        if (colWidths_[col] + adj < minW) adj = minW - colWidths_[col];
        int rightSpace = colWidths_[col + 1] - minW;
        if (adj > rightSpace) adj = rightSpace;
        colWidths_[col] += adj;
        colWidths_[col + 1] -= adj;
        colResizeDragDelta_ += adj;
        colResizeEndX_ = x;
        InvalidateRect(g_hwndMain, nullptr, FALSE);
        return;
    }

    // Column resize hover detection
    if (selectedDay_ >= 0 && Contains(detailPanelRect_, x, y)) {
        const int inset = ScaleIntHelper(12);
        int colX[kColCount + 1];
        GetColumnRects(detailPanelRect_, inset, colWidths_, colX);
        int newDivider = -1;
        for (int i = 1; i <= kColDividerCount; ++i) {
            if (std::abs(x - colX[i]) <= 5) { newDivider = i - 1; break; }
        }
        if (newDivider != colDragDivider_) {
            colDragDivider_ = newDivider;
            if (colDragDivider_ >= 0)
                SetCursor(LoadCursorW(nullptr, IDC_SIZEWE));
            else
                SetCursor(LoadCursorW(nullptr, IDC_ARROW));
            InvalidateRect(g_hwndMain, nullptr, FALSE);
        }
    } else if (colDragDivider_ >= 0) {
        colDragDivider_ = -1;
        SetCursor(LoadCursorW(nullptr, IDC_ARROW));
        InvalidateRect(g_hwndMain, nullptr, FALSE);
    }

    const int nextCommit = CommitAt(x, y);
    if (next != hoveredDay_ || nextCommit != hoveredCommit_) {
        hoveredDay_ = next;
        hoveredCommit_ = nextCommit;
        InvalidateRect(g_hwndMain, nullptr, FALSE);
    } else if (hoveredDay_ >= 0) {
        InvalidateRect(g_hwndMain, nullptr, FALSE);
    }
}

void UiDraw::MouseLeave() {
    if (colDragDivider_ >= 0 || colResizeState_ == ColResizeState::Dragging) {
        colDragDivider_ = -1;
        colResizeState_ = ColResizeState::None;
        colResizeColumn_ = -1;
        colResizeEndX_ = 0;
        colResizeDragDelta_ = 0;
        SetCursor(LoadCursorW(nullptr, IDC_ARROW));
    }
    if (hoveredDay_ >= 0 || hoveredCommit_ >= 0) {
        hoveredDay_ = -1;
        hoveredCommit_ = -1;
        InvalidateRect(g_hwndMain, nullptr, FALSE);
    }
}

void UiDraw::MouseDown(int x, int y) {
    if (selectedDay_ < 0 || colDragDivider_ < 0) return;
    if (!Contains(detailPanelRect_, x, y)) return;
    colResizeState_ = ColResizeState::Dragging;
    colResizeColumn_ = colDragDivider_;
    colResizeEndX_ = x;
    // Record divider position at start for guide line
    const int inset = ScaleIntHelper(12);
    int colX[kColCount + 1];
    GetColumnRects(detailPanelRect_, inset, colWidths_, colX);
    colResizeStartDividerX_ = colX[colResizeColumn_];
    colResizeDragDelta_ = 0;
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void UiDraw::MouseUp(int x, int y) {
    if (colResizeState_ != ColResizeState::Dragging || colResizeColumn_ < 0) return;
    if (!Contains(detailPanelRect_, x, y)) {
        // Drag ended outside panel — cancel without saving
        colResizeState_ = ColResizeState::None;
        colResizeColumn_ = -1;
        colResizeEndX_ = 0;
        colResizeDragDelta_ = 0;
        colResizeStartDividerX_ = 0;
        colDragDivider_ = -1;
        SetCursor(LoadCursorW(nullptr, IDC_ARROW));
        return;
    }
    colResizeState_ = ColResizeState::None;
    colResizeColumn_ = -1;
    colResizeEndX_ = 0;
    colResizeDragDelta_ = 0;
    colDragDivider_ = -1;
    SetCursor(LoadCursorW(nullptr, IDC_ARROW));
    // Persist column widths to config
    g_config.columnWidths.resize(kColCount);
    for (int i = 0; i < kColCount; ++i)
        g_config.columnWidths[i] = static_cast<int>(colWidths_[i] / g_fontScale);
    std::wstring saveError;
    SaveAppConfig(g_config, saveError);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}


void UiDraw::MouseWheel(int x, int y, int delta) {
    if (selectedDay_ >= 0 && Contains(detailPanelRect_, x, y)) {
        commitScroll_ -= delta / WHEEL_DELTA * 3;
        const int totalRows = static_cast<int>(g_contributionData.days[selectedDay_].commits.size());
        commitScroll_ = std::max(0, std::min(commitScroll_, std::max(0, totalRows - VisibleCommitRows())));
        hoveredCommit_ = CommitAt(x, y);
        InvalidateRect(g_hwndMain, nullptr, FALSE);
        return;
    }
    if (x >= ScaleIntHelper(SIDEBAR_W) || y < ScaleIntHelper(TOPBAR_H)) return;
    g_repoScroll -= delta / WHEEL_DELTA * 3;
    g_repoScroll = std::max(0, g_repoScroll);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void UiDraw::SelectDay(int index) {
    selectedDay_ = index;
    commitScroll_ = 0;
    hoveredCommit_ = -1;
    LoadDayCommits(index);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void UiDraw::ClearDaySelection() {
    selectedDay_ = -1;
    commitScroll_ = 0;
    hoveredCommit_ = -1;
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void UiDraw::DrawDayDetailPanel(HDC dc) {
    if (selectedDay_ < 0 || selectedDay_ >= static_cast<int>(g_contributionData.days.size())) return;
    DayEntry& day = g_contributionData.days[selectedDay_];

    RECT panel = detailPanelRect_;
    if (panel.bottom <= panel.top) return;

    constexpr int cornerRadius = 6;
    RoundRect(dc, panel, cornerRadius, ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE), ThemeColor(CLR_BORDER, CLR_DARK_BORDER));

    const int titleHeight = ScaleIntHelper(36);
    const int columnsHeight = ScaleIntHelper(26);
    const int baseRowHeight = ScaleIntHelper(40);
    const int rowHeight = baseRowHeight + ScaleIntHelper(12);  // Increased for multi-line support
    const int inset = ScaleIntHelper(12);

    RECT titleRect = {panel.left, panel.top, panel.right, panel.top + titleHeight};
    Fill(dc, titleRect, ThemeColor(CLR_BG_HOVER, CLR_DARK_BG_HOVER));
    const int visibleRows = VisibleCommitRows();
    const int totalRows = static_cast<int>(day.commits.size());
    commitScroll_ = std::max(0, std::min(commitScroll_, std::max(0, totalRows - visibleRows)));
    const int firstVisible = totalRows ? commitScroll_ + 1 : 0;
    const int lastVisible = std::min(totalRows, commitScroll_ + visibleRows);

    RECT titleText = {titleRect.left + inset, titleRect.top, titleRect.right - ScaleIntHelper(150), titleRect.bottom};
    Text(dc, day.date + L" · " + FormatInteger(day.count) + L" 次提交 · " +
             FormatInteger(static_cast<int>(day.details.size())) + L" 个项目",
         titleText, ScaleIntHelper(12), ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY),
         DT_LEFT | DT_VCENTER | DT_SINGLELINE, FW_SEMIBOLD);
    if (totalRows) {
        RECT rangeRect = {panel.right - ScaleIntHelper(146), panel.top, panel.right - ScaleIntHelper(40), panel.top + titleHeight};
        Text(dc, FormatInteger(firstVisible) + L"-" + FormatInteger(lastVisible) + L" / " + FormatInteger(totalRows),
             rangeRect, ScaleIntHelper(9), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC),
             DT_RIGHT | DT_VCENTER | DT_SINGLELINE);
    }
    RECT closeRect = {panel.right - ScaleIntHelper(36), panel.top, panel.right, panel.top + titleHeight};
    Text(dc, L"×", closeRect, ScaleIntHelper(12), ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC),
         DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    int colX[kColCount + 1];
    GetColumnRects(panel, inset, colWidths_, colX);

    // Column header labels
    RECT columns = {panel.left, titleRect.bottom, panel.right, titleRect.bottom + columnsHeight};
    Fill(dc, columns, ThemeColor(CLR_BG_SURFACE, CLR_DARK_BG_SURFACE));
    const COLORREF secondary = ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC);
    Text(dc, L"时间", {colX[0], columns.top, colX[1], columns.bottom}, ScaleIntHelper(9), secondary);
    Text(dc, L"项目", {colX[1], columns.top, colX[2], columns.bottom}, ScaleIntHelper(9), secondary);
    Text(dc, L"提交说明", {colX[2], columns.top, colX[3], columns.bottom}, ScaleIntHelper(9), secondary);
    Text(dc, L"作者", {colX[3], columns.top, colX[4], columns.bottom}, ScaleIntHelper(9), secondary);
    Text(dc, L"哈希", {colX[4], columns.top, colX[5], columns.bottom}, ScaleIntHelper(9), secondary);
    Line(dc, panel.left, columns.bottom - 1, panel.right, columns.bottom - 1,
         ThemeColor(CLR_BORDER, CLR_DARK_BORDER));

    // Draw vertical divider lines (resize handles)
    const COLORREF dividerColor = ThemeColor(CLR_BORDER, CLR_DARK_BORDER);
    const int dividerY1 = columns.top, dividerY2 = panel.bottom;
    for (int i = 1; i < kColCount; ++i) {
        Line(dc, colX[i], dividerY1, colX[i], dividerY2, dividerColor);
    }

    // Draw resize handle on header (hover or drag active)
    const int kHandleH = ScaleIntHelper(16);
    if (colDragDivider_ >= 0 && colResizeState_ == ColResizeState::None) {
        const int dx = colX[colDragDivider_];
        const int dy = columns.top + (columnsHeight - kHandleH) / 2;
        RECT handleRect = {dx - 2, dy, dx + 2, dy + kHandleH};
        Fill(dc, handleRect, ThemeColor(RGB(31,136,61), RGB(63,185,80)));
    }

    // Draw drag guide line while resizing
    if (colResizeState_ == ColResizeState::Dragging && colResizeColumn_ >= 0) {
        // Compute current divider position based on modified colWidths
        int inset = ScaleIntHelper(12);
        GetColumnRects(panel, inset, colWidths_, colX);
        const int guideX = colX[colResizeColumn_];
        const int guideColor = ThemeColor(RGB(31,136,61), RGB(63,185,80));
        HPEN pen = CreatePen(PS_DOT, 1, guideColor);
        HPEN oldPen = static_cast<HPEN>(SelectObject(dc, pen));
        SetROP2(dc, R2_NOTXORPEN);
        Line(dc, guideX, columns.top, guideX, panel.bottom, guideColor);
        SetROP2(dc, R2_COPYPEN);
        SelectObject(dc, oldPen);
        DeleteObject(pen);
    }

    RECT listRect = {panel.left, columns.bottom, panel.right, panel.bottom};

    if (day.commits.empty()) {
        RECT empty = {listRect.left + ScaleIntHelper(8), listRect.top + ScaleIntHelper(8),
                      listRect.right - ScaleIntHelper(8), listRect.bottom};
        const std::wstring message = !day.commitError.empty()
            ? L"提交明细读取失败：" + day.commitError
            : (day.count > 0 ? L"该日期的提交明细尚未建立" : L"当天没有提交记录");
        Text(dc, message, empty, ScaleIntHelper(11), secondary,
             DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        return;
    }

    for (int i = 0; i < visibleRows && commitScroll_ + i < totalRows; ++i) {
        const DayEntry::CommitEntry& commit = day.commits[commitScroll_ + i];
        const int y = listRect.top + i * rowHeight;
        RECT rowRect = {listRect.left, y, listRect.right, y + rowHeight};
        if (commitScroll_ + i == hoveredCommit_)
            Fill(dc, rowRect, ThemeColor(CLR_BG_HOVER, CLR_DARK_BG_HOVER));
        else if (i % 2 == 0)
            Fill(dc, rowRect, ThemeColor(CLR_BG_PAGE, CLR_DARK_BG_PAGE));
        const int x0 = colX[0], x1 = colX[1], x2 = colX[2], x3 = colX[3], x4 = colX[4], x5 = colX[5];
        Text(dc, commit.time, {x0, y, x1 - ScaleIntHelper(8), y + rowHeight}, ScaleIntHelper(10), secondary,
             DT_LEFT | DT_VCENTER | DT_SINGLELINE);
        Text(dc, commit.repoName, {x1, y, x2 - ScaleIntHelper(10), y + rowHeight}, ScaleIntHelper(10), secondary,
             DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        const std::wstring subject = commit.message.empty() ? L"(无提交说明)" : commit.message;
        // Message column: wrap to fit column width
        Text(dc, subject, {x2, y, x3 - ScaleIntHelper(10), y + rowHeight}, ScaleIntHelper(11),
             ThemeColor(CLR_TEXT_PRIMARY, CLR_DARK_TEXT_PRIMARY),
             DT_LEFT | DT_WORDBREAK | DT_TOP | DT_END_ELLIPSIS);
        Text(dc, commit.author, {x3, y, x4 - ScaleIntHelper(10), y + rowHeight}, ScaleIntHelper(10), secondary,
             DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        const std::wstring shortHash = commit.hash.size() > 7 ? commit.hash.substr(0, 7) : commit.hash;
        Text(dc, shortHash, {x4, y, x5, y + rowHeight}, ScaleIntHelper(10), secondary,
             DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        Line(dc, panel.left, rowRect.bottom - 1, panel.right, rowRect.bottom - 1,
             ThemeColor(CLR_BORDER, CLR_DARK_BORDER));
    }

    if (totalRows > visibleRows) {
        RECT track = {panel.right - ScaleIntHelper(4), listRect.top, panel.right - ScaleIntHelper(1), listRect.bottom};
        Fill(dc, track, ThemeColor(CLR_BORDER, CLR_DARK_BORDER));
        const int trackHeight = track.bottom - track.top;
        const int thumbHeight = std::max(ScaleIntHelper(18), trackHeight * visibleRows / totalRows);
        const int range = std::max(1, totalRows - visibleRows);
        const int thumbTop = track.top + (trackHeight - thumbHeight) * commitScroll_ / range;
        Fill(dc, {track.left, thumbTop, track.right, thumbTop + thumbHeight},
             ThemeColor(CLR_TEXT_TERTIARY, CLR_DARK_TEXT_SEC));
    }
}
