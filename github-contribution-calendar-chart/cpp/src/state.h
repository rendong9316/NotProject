#pragma once

#include "types.h"
#include "config.h"

#include <atomic>
#include <string>
#include <unordered_map>
#include <vector>

extern HWND g_hwndMain;
extern HWND g_hwndSearch;
extern Theme g_theme;
extern bool g_loading;
extern OperationKind g_operationKind;
extern std::wstring g_error;
extern std::wstring g_status;
extern std::wstring g_dataDirectory;
extern std::wstring g_gitVersion;
extern int g_year;
extern int g_repoScroll;
extern std::vector<Repository> g_repos;
extern std::unordered_map<std::wstring, bool> g_selected;
extern std::wstring g_query;
extern ContributionData g_contributionData;
extern double g_fontScale;
extern AppConfig g_config;
extern AiResult g_aiResult;

bool InitializeState(HWND hwnd);
void ShutdownState();
void StartRefresh();
void StartDiscovery();
void HandleOperationDone(LPARAM resultPointer);
void HandleOperationProgress(WPARAM completed, LPARAM remaining);
void RebuildContributions();
void ToggleRepository(const std::wstring& id);
void SelectAllVisible(bool selected);
void SetSearchQuery(const std::wstring& query);
void ChangeYear(int delta);
void AdjustFontSize(int delta);
void ToggleTheme();
void ApplyDarkMode(HWND hwnd, bool dark);
std::vector<size_t> VisibleRepositoryIndices();
int SelectedRepositoryCount();
void SortReposByYearTotal();
void LoadDayCommits(int dayIndex);
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
