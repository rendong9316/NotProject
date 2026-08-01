#pragma once

#include <windows.h>

class UiDraw {
public:
    void Resize(int width, int height);
    void Paint(HDC target, int width, int height);
    void LayoutSearch(HWND edit) const;
    void MouseMove(int x, int y);
    void MouseLeave();
    bool Click(int x, int y);
    bool RightClick(int x, int y);
    void MouseWheel(int x, int y, int delta);

private:
    void ComputeLayout(int width, int height);
    int RepositoryAt(int x, int y) const;
    void DrawTopbar(HDC dc);
    void DrawSidebar(HDC dc);
    void DrawContent(HDC dc);
    void DrawCalendar(HDC dc);
    void DrawRanking(HDC dc);
    void DrawStatusbar(HDC dc);
    void DrawTooltip(HDC dc);

    double fontScale_ = 1.0;
    int Scale(int value) const { return static_cast<int>(value * fontScale_); }

    RECT refreshRect_ = {};
    RECT discoverRect_ = {};
    RECT themeRect_ = {};
    RECT yearPreviousRect_ = {};
    RECT yearNextRect_ = {};
    RECT selectAllRect_ = {};
    RECT searchRect_ = {};
    RECT repoListRect_ = {};
    RECT calendarRect_ = {};
    int width_ = 0;
    int height_ = 0;
    int daySize_ = 12;
    int dayGap_ = 3;
    int hoveredDay_ = -1;
    int mouseX_ = 0;
    int mouseY_ = 0;
};

extern UiDraw g_ui;
