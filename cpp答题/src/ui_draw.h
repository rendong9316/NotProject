#pragma once
#include "types.h"
#include <windows.h>
#include <gdiplus.h>
#include <string>

// CHOICE_OPTION_COUNT must be available here for extern array declaration
static const int CHOICE_OPTION_COUNT = 4;

using namespace Gdiplus;                              // using namespace：简化代码，不需要写 Gdiplus:: 前缀

// Global layout values (set from main thread)
// g_clientW/H: 窗口客户区的物理像素尺寸（缩放后）
// g_w/h: 逻辑像素尺寸（client 尺寸除以 g_fontScale），用于所有布局计算
// g_hdcMem: 内存设备上下文，双缓冲绘图的离屏画布
// g_hbmMem: 内存位图，配合 hdcMem 使用
// g_fontScale: 字体/布局缩放比例（1.0 ~ 3.0）
// g_hwndMain: 主窗口句柄，用于 ResizeBackBuffer 时失效重绘
extern int g_clientW, g_clientH;
extern int g_w, g_h;
extern HDC g_hdcMem;
extern HBITMAP g_hbmMem;
extern float g_fontScale;
extern HWND g_hwndMain;

const int INITIAL_WINDOW_W = 2440;                    // 初始窗口宽度（物理像素）
const int INITIAL_WINDOW_H = 1760;                    // 初始窗口高度（物理像素）

// UI colors — 统一的配色方案
static const COLORREF CLR_BG = RGB(232, 238, 247);    // 页面背景渐变底色
static const COLORREF CLR_PANEL = RGB(255, 255, 255); // 卡片白色面板底色
static const COLORREF CLR_INK = RGB(20, 30, 48);      // 主要文字颜色（深蓝灰，接近纯黑）
static const COLORREF CLR_MUTED = RGB(82, 96, 118);   // 次要/禁用文字颜色（灰色）
static const COLORREF CLR_NAVY = RGB(13, 42, 86);     // 品牌主色（深蓝）
static const COLORREF CLR_NAVY_2 = RGB(20, 64, 122);  // 品牌主色变体（稍亮蓝）
static const COLORREF CLR_BLUE = RGB(0, 91, 211);     // 选项选中色（亮蓝）
static const COLORREF CLR_GOLD = RGB(214, 143, 28);   // 倒计时警告色（金色/琥珀）
static const COLORREF CLR_GREEN = RGB(0, 142, 73);    // 答对/成功色
static const COLORREF CLR_RED = RGB(205, 38, 38);     // 答错/超时/危险色
static const COLORREF CLR_LINE = RGB(196, 208, 224);  // 分割线和边框的淡灰色
static const COLORREF CLR_SOFT = RGB(244, 248, 253);  // 辅助卡片的柔和底色

void UpdateLayoutSize();                              // 根据当前缩放比和客户区大小更新逻辑分辨率
int ScaleCoord(int v);                                // 将逻辑坐标值缩放到物理像素值
void ResizeBackBuffer(HWND hwnd);                     // 调整双缓冲离屏画布尺寸

// UIRect definitions (external — populated during DrawQuizPage for click detection)
extern UIRect g_choiceOptionRects[CHOICE_OPTION_COUNT];
extern int g_choiceOptionRectCount;

UIRect HomeButtonRect(int idx);                       // 主页三个模式按钮的位置
UIRect FillScoreButtonRect(int idx);                  // 分值选择页六个分值按钮的位置
UIRect ReturnHomeButtonRect();                        // 返回主页按钮位置（多页共用）
UIRect FillEditRect();                                // 填空题编辑框位置
UIRect ConfirmButtonRect();                           // 确认/下一题按钮位置

// Drawing functions
void DrawBackground(Graphics& g);                     // 绘制通用背景（渐变底色 + 顶部横条）
void DrawHeader(Graphics& g, const std::wstring& rightText = L"");  // 绘制页面顶栏标题
void DrawMetric(Graphics& g, int x, int y, int w, int h, const std::wstring& label, const std::wstring& value, COLORREF accent); // 绘制单个指标卡片
void DrawButton(Graphics& g, int x, int y, int w, int h, const std::wstring& text, COLORREF c1, COLORREF c2, bool outline = false); // 绘制按钮
void DrawFontControls(Graphics& g);                   // 绘制右上角 A- / A+ 字体缩放按钮
void DrawHomePage(Graphics& g);                       // 主页绘制
void DrawFillScoreSelectPage(Graphics& g);            // 风险题分值选择页绘制
void DrawQuizPage(Graphics& g);                       // 答题页绘制
void DrawResultPage(Graphics& g);                     // 结果页绘制
