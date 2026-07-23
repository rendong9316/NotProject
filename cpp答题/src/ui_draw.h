#pragma once
#include "types.h"
#include <windows.h>
#include <gdiplus.h>
#include <string>

// CHOICE_OPTION_COUNT must be available here for extern array declaration
static const int CHOICE_OPTION_COUNT = 4;

using namespace Gdiplus;

extern int g_clientW, g_clientH;
extern int g_w, g_h;
extern HDC g_hdcMem;
extern HBITMAP g_hbmMem;
extern float g_fontScale;
extern HWND g_hwndMain;

const int INITIAL_WINDOW_W = 2440;
const int INITIAL_WINDOW_H = 1760;

static const COLORREF CLR_BG = RGB(232, 238, 247);
static const COLORREF CLR_PANEL = RGB(255, 255, 255);
static const COLORREF CLR_INK = RGB(20, 30, 48);
static const COLORREF CLR_MUTED = RGB(82, 96, 118);
static const COLORREF CLR_NAVY = RGB(13, 42, 86);
static const COLORREF CLR_NAVY_2 = RGB(20, 64, 122);
static const COLORREF CLR_BLUE = RGB(0, 91, 211);
static const COLORREF CLR_GOLD = RGB(214, 143, 28);
static const COLORREF CLR_GREEN = RGB(0, 142, 73);
static const COLORREF CLR_RED = RGB(205, 38, 38);
static const COLORREF CLR_LINE = RGB(196, 208, 224);
static const COLORREF CLR_SOFT = RGB(244, 248, 253);

void UpdateLayoutSize();
int ScaleCoord(int v);
void ResizeBackBuffer(HWND hwnd);

// UIRect definitions (external — populated during DrawQuizPage for click detection)
extern UIRect g_choiceOptionRects[CHOICE_OPTION_COUNT];
extern int g_choiceOptionRectCount;

UIRect HomeButtonRect(int idx);
UIRect FillScoreButtonRect(int idx);
UIRect ReturnHomeButtonRect();
UIRect FillEditRect();
UIRect ConfirmButtonRect();

// Drawing functions
void DrawBackground(Graphics& g);
void DrawHeader(Graphics& g, const std::wstring& rightText = L"");
void DrawMetric(Graphics& g, int x, int y, int w, int h, const std::wstring& label, const std::wstring& value, COLORREF accent);
void DrawButton(Graphics& g, int x, int y, int w, int h, const std::wstring& text, COLORREF c1, COLORREF c2, bool outline = false);
void DrawFontControls(Graphics& g);
void DrawHomePage(Graphics& g);
void DrawFillScoreSelectPage(Graphics& g);
void DrawQuizPage(Graphics& g);
void DrawResultPage(Graphics& g);
