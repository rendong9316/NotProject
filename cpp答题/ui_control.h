#pragma once
#include "types.h"
#include <windows.h>

// Forward declaration of WndProc
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);

// Hit helper (defined in ui_draw.cpp)
bool Hit(int mx, int my, int x, int y, int w, int h);

// Edit control management
void ApplyEditFont();
void EnsureEditControl(HWND parent);
void UpdateEditForState();
void DestroyEditControl();
void ReadEditIntoState();

// Font adjustment (declared in ui_draw.h, implemented in ui_draw.cpp)
void AdjustFont(float delta);

// Title bar dark mode
void ApplyTitleBarStyle(HWND hwnd);

// Global handles defined in ui_control.cpp
extern HWND g_hwndMain;
extern HWND g_hwndEdit;
