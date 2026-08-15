#pragma once

#include <windows.h>
#include <cstdint>
#include <string>
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
    void OpenAgentWorkspace(const std::wstring& dayDate = L"");
    void CloseAgentWorkspace();

    // Agent text selection state
    struct AgentTextSelection {
        int messageIndex = -1;
        int charStart = -1;
        int charEnd = -1;
        // Screen-space rect for selection highlight
        RECT selRect = {};
        // Copy button rect
        RECT copyBtnRect = {};
    };

private:
    void ComputeLayout(int width, int height);
    int RepositoryAt(int x, int y) const;
    int CommitAt(int x, int y) const;
    int AgentMessageAt(int x, int y) const;
    int AgentCharIndexAt(int x, int y, int msgIndex, int scroll) const;
    void AgentClearSelection();
    void AgentStartSelection(int x, int y);
    void AgentUpdateSelection(int x, int y);
    void AgentFinishSelection();
    void AgentDrawSelection(HDC dc) const;
    void AgentDrawCopyButton(HDC dc) const;
    bool AgentCopySelected() const;
    void MouseDown(int x, int y);
    void MouseUp(int x, int y);

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
    void DrawAgentPanel(HDC dc);
    void DrawStatusbar(HDC dc);
    void DrawTooltip(HDC dc);

    RECT topbarRect_ = {};
    RECT statusbarRect_ = {};
    RECT refreshRect_ = {};
    RECT discoverRect_ = {};
    RECT themeRect_ = {};
    RECT calendarTabRect_ = {};
    RECT aiTabRect_ = {};
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
    int agentScroll_ = 0;
    int agentContentHeight_ = 0;
    int agentViewportHeight_ = 0;
    std::uint64_t agentSeenRevision_ = 0;
    std::wstring agentDisplayedSession_;
    bool agentAutoFollow_ = true;
    int mouseX_ = 0;
    int mouseY_ = 0;
    bool aiWorkspace_ = false;
    bool agentTextSelecting_ = false;
    int agentDragStartX_ = 0;
    int agentDragStartY_ = 0;
    AgentTextSelection agentTextSelection_;

    // Column resize state
    enum class ColResizeState { None, Dragging };
    ColResizeState colResizeState_ = ColResizeState::None;
    int colResizeColumn_ = -1;       // 0=time,1=repo,2=message,3=author; -1=dragging; -2=sidebar hover
    int colResizeEndX_ = 0;          // absolute X where drag started (mouse down position)
    int colResizeDragDelta_ = 0;     // pixel offset accumulated during current drag
    int colResizeStartDividerX_ = 0; // X position of the divider at drag start (for guide line)
    int sidebarResizeStartWidth_ = 0; // sidebarWidth_ at drag start (sidebar mode only)
    std::vector<int> colWidths_;     // 5 saved widths at fontScale=1.0
    int colDragDivider_ = -1;        // which divider the cursor is hovering over (-2 = sidebar)
};

extern UiDraw g_ui;

void RefreshSearchControlScale();
void StartAiAnalysis(int dayIndex);
