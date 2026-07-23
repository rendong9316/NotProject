// ============================================================================
// ui_control.h — UI 交互控制头文件
// 声明窗口消息处理回调、点击交互相关函数
// ============================================================================

#pragma once

#include "types.h"                                     // 使用 Page 枚举类型
#include <windows.h>                                   // HWND 窗口句柄类型

// Forward declaration of WndProc（窗口过程回调函数的完整实现在 ui_control.cpp 中）
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);

// Hit helper（点击检测，实现在 ui_draw.cpp 中）
bool Hit(int mx, int my, int x, int y, int w, int h);  // 判断鼠标坐标(mx,my)是否在矩形(x,y,w,h)内

// Edit control management（编辑控件管理 — 填空题的文字输入框）
void ApplyEditFont();                                   // 应用当前缩放比例下的字体到编辑控件
void EnsureEditControl(HWND parent);                    // 如果编辑控件尚未创建则创建它
void UpdateEditForState();                              // 根据当前状态更新编辑控件的显示/隐藏
void DestroyEditControl();                              // 销毁编辑控件并释放字体对象
void ReadEditIntoState();                               // 读取编辑控件中的文本到 g_state.userFill

// Font adjustment（字体调整，实现在 ui_draw.cpp 中，此处仅声明避免循环依赖）
void AdjustFont(float delta);                           // 按 delta 调整全局字体缩放比例

// Title bar dark mode（标题栏深色模式）
void ApplyTitleBarStyle(HWND hwnd);                     // 应用深色标题栏样式（Windows 10+ DWM API）

// Global handles defined in ui_control.cpp（全局窗口句柄声明）
extern HWND g_hwndMain;                                 // 主窗口句柄
extern HWND g_hwndEdit;                                 // 填空题输入编辑框句柄
