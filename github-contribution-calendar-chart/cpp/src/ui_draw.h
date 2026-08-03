#pragma once

#include <windows.h>
#include <vector>

class UiDraw {
public:
    void Resize(int width, int height);
    void Paint(HDC target, int width, int height);
    void LayoutSearch(HWND edit) const;
    void MouseMove(int x, int y);
    void MouseLeave();
    bool Click(int x, int y);
    bool RightClick(int x, int y);
    void MouseDown(int x, int y);
    void MouseUp(int x, int y);
    void MouseWheel(int x, int y, int delta);

    void SelectDay(int index);
    void ClearDaySelection();
    void InitColumnWidths();
    int VisibleRankingRows() const;

private:
    void ComputeLayout(int width, int height);
    int RepositoryAt(int x, int y) const;
    int CommitAt(int x, int y) const;
    int VisibleCommitRows() const;
    void DrawTopbar(HDC dc);
    void DrawSidebar(HDC dc);
    void DrawContent(HDC dc);
    void DrawCalendar(HDC dc);
    void DrawRanking(HDC dc);
    void DrawDayDetailPanel(HDC dc);
    void DrawStatusbar(HDC dc);
    void DrawTooltip(HDC dc);

    RECT topbarRect_ = {};
    RECT statusbarRect_ = {};
    RECT refreshRect_ = {};
    RECT discoverRect_ = {};
    RECT themeRect_ = {};
    RECT yearPreviousRect_ = {};
    RECT yearNextRect_ = {};
    RECT selectAllRect_ = {};
    RECT searchRect_ = {};
    RECT repoListRect_ = {};
    RECT calendarRect_ = {};
    RECT rankingRect_ = {};
    RECT detailPanelRect_ = {};
    int width_ = 0;
    int height_ = 0;
    int sidebarWidth_ = 250;
    int daySize_ = 12;
    int dayGap_ = 3;
    int hoveredDay_ = -1;
    int hoveredCommit_ = -1;
    int selectedDay_ = -1;
    int commitScroll_ = 0;
    int repoScroll_ = 0;
    int mouseX_ = 0;
    int mouseY_ = 0;

    // Column resize state
    enum class ColResizeState { None, Dragging };
    ColResizeState colResizeState_ = ColResizeState::None;
    int colResizeColumn_ = -1;       // 0=time,1=repo,2=message,3=author
    int colResizeEndX_ = 0;          // absolute X where drag started (mouse down position)
    int colResizeDragDelta_ = 0;     // pixel offset accumulated during current drag
    int colResizeStartDividerX_ = 0; // X position of the divider at drag start (for guide line)
    std::vector<int> colWidths_;     // 5 saved widths at fontScale=1.0
    int colDragDivider_ = -1;        // which divider the cursor is hovering over
};

extern UiDraw g_ui;

void RefreshSearchControlScale();
