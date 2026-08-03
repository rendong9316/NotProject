#include "state.h"

#include "cache_store.h"
#include "git_scan.h"
#include "platform.h"
#include "ui_draw.h"

#include <windows.h>
#include <algorithm>
#include <atomic>
#include <map>
#include <mutex>
#include <set>
#include <thread>

HWND g_hwndMain = nullptr;
HWND g_hwndSearch = nullptr;
Theme g_theme = Theme::Light;
bool g_loading = false;
OperationKind g_operationKind = OperationKind::None;
std::wstring g_error;
std::wstring g_status;
std::wstring g_dataDirectory;
std::wstring g_gitVersion;
int g_year = 0;
int g_repoScroll = 0;
std::vector<Repository> g_repos;
std::unordered_map<std::wstring, bool> g_selected;
std::wstring g_query;
ContributionData g_contributionData;
double g_fontScale = 1.0;
AppConfig g_config;

namespace {

struct OperationResult {
    std::vector<Repository> repositories;
    std::vector<std::wstring> roots;
    std::wstring lastDiscovery;
    std::wstring lastRefresh;
    std::wstring error;
    OperationSummary summary;
};

CacheStore g_store;
std::wstring g_lastDiscovery;
std::wstring g_lastRefresh;
std::vector<std::wstring> g_scanRoots;
std::thread g_worker;
std::atomic<bool> g_cancel(false);

struct RepositoryUpdate {
    Repository repository;
    enum Status { Added, Updated, Unchanged, Unavailable } status = Unavailable;
};

std::wstring TimestampWide() { return Utf8ToWide(IsoTimestampUtc()); }

// Note: FileMtimeHex is no longer used for incremental refresh optimization
// because .git/HEAD mtime is unreliable (HEAD is a symbolic ref). Kept for
// potential future use or debugging.
std::wstring FileMtimeHex(const std::wstring& path) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                           nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == INVALID_HANDLE_VALUE) return L"";
    FILETIME ft = {};
    BOOL ok = GetFileTime(h, nullptr, nullptr, &ft);
    CloseHandle(h);
    if (!ok) return L"";
    std::wstring result;
    result.reserve(16);
    wchar_t buf[17] = {};
    _snwprintf_s(buf, _countof(buf), _TRUNCATE, L"%08x%08x", ft.dwHighDateTime, ft.dwLowDateTime);
    result = buf;
    return result;
}

bool SamePath(const std::wstring& left, const std::wstring& right) {
    return Lowercase(left) == Lowercase(right);
}

std::vector<std::wstring> ResolveRoots() {
    std::vector<std::wstring> roots = g_config.scanAllDrives ? LocalDriveRoots() : std::vector<std::wstring>();
    roots.insert(roots.end(), g_config.scanRoots.begin(), g_config.scanRoots.end());
    std::vector<std::wstring> unique;
    for (const std::wstring& root : roots) {
        if (!DirectoryExists(root)) continue;
        bool duplicate = false;
        for (const std::wstring& existing : unique) if (SamePath(existing, root)) { duplicate = true; break; }
        if (!duplicate) unique.push_back(root);
    }
    return unique;
}

RepositoryUpdate UpdateRepository(const std::wstring& path, const Repository* existing) {
    RepositoryUpdate update;
    update.repository = existing ? *existing : Repository();
    update.repository.id = GitScan::RepositoryId(path);
    update.repository.name = BaseName(path);
    update.repository.path = path;
    update.repository.checkedAt = TimestampWide();
    update.repository.filterSignature = GitScan::FilterSignature(g_config);

    // Incremental check: skip CollectHistory only if fingerprint is unchanged.
    // We do NOT use .git/HEAD mtime as an optimization because HEAD is a
    // symbolic ref (contains "ref: refs/heads/main") that doesn't change when
    // new commits are added to the current branch. Only the target ref file
    // (e.g., .git/refs/heads/main) changes, so HEAD mtime is unreliable.
    std::wstring fingerprint;
    std::wstring error;
    if (!DirectoryExists(path) || !GitScan::ReadFingerprint(path, fingerprint, error)) {
        update.repository.available = false;
        update.repository.error = error.empty() ? L"仓库路径当前不可用" : error;
        update.status = RepositoryUpdate::Unavailable;
        return update;
    }
    update.repository.available = true;
    update.repository.error.clear();
    update.repository.fingerprint = fingerprint;
    const bool unchanged = existing && existing->fingerprint == fingerprint &&
                           GitScan::MatchesFilterSignature(g_config, existing->filterSignature) &&
                           g_store.HasCompleteHistory(update.repository.id);
    if (unchanged) {
        update.status = RepositoryUpdate::Unchanged;
        return update;
    }

    std::map<std::wstring, int> days;
    std::vector<DayEntry::CommitEntry> commits;
    if (!GitScan::CollectHistory(path, g_config, days, commits, error)) {
        update.repository.available = false;
        update.repository.error = error;
        update.status = RepositoryUpdate::Unavailable;
        return update;
    }
    update.repository.updatedAt = TimestampWide();
    std::wstring saveTime;
    if (!g_store.SaveHistory(update.repository, days, commits, error, &saveTime)) {
        update.repository.available = false;
        update.repository.error = error;
        update.status = RepositoryUpdate::Unavailable;
        return update;
    }
    update.repository.historySaveTime = saveTime;
    update.status = existing ? RepositoryUpdate::Updated : RepositoryUpdate::Added;
    return update;
}

std::vector<RepositoryUpdate> UpdateMany(const std::vector<std::pair<std::wstring, const Repository*>>& work) {
    std::vector<RepositoryUpdate> results(work.size());
    std::atomic<size_t> next(0);
    std::atomic<int> completed(0);
    const int threadCount = std::max(1, std::min<int>(g_config.gitConcurrency, static_cast<int>(work.size())));
    std::vector<std::thread> workers;
    for (int worker = 0; worker < threadCount; ++worker) {
        workers.emplace_back([&]() {
            while (!g_cancel.load()) {
                const size_t index = next.fetch_add(1);
                if (index >= work.size()) return;
                results[index] = UpdateRepository(work[index].first, work[index].second);
                const int done = ++completed;
                PostMessageW(g_hwndMain, WM_APP_PROGRESS, done, static_cast<LPARAM>(work.size() - done));
            }
        });
    }
    for (std::thread& worker : workers) worker.join();
    if (g_cancel.load()) results.resize(std::min(next.load(), results.size()));
    return results;
}

void CountResult(OperationSummary& summary, RepositoryUpdate::Status status) {
    ++summary.checked;
    if (status == RepositoryUpdate::Added) ++summary.added;
    else if (status == RepositoryUpdate::Updated) ++summary.updated;
    else if (status == RepositoryUpdate::Unchanged) ++summary.unchanged;
    else ++summary.unavailable;
}

void RunOperation(OperationKind kind, std::vector<Repository> existing,
                  std::vector<std::wstring> existingRoots,
                  std::wstring previousDiscovery, std::wstring previousRefresh) {
    const unsigned long long started = GetTickCount64();
    OperationResult* result = new OperationResult();
    result->repositories = existing;
    result->roots = existingRoots;
    result->lastDiscovery = previousDiscovery;
    result->lastRefresh = previousRefresh;
    result->summary.kind = kind == OperationKind::Discover ? L"discover" : L"refresh";

    if (kind == OperationKind::Discover) {
        result->roots = ResolveRoots();
        if (result->roots.empty()) {
            result->error = L"没有找到可扫描的本地磁盘。";
        } else {
            const std::vector<std::wstring> paths = GitScan::FindRepositories(
                result->roots, g_config.maxScanDepth, g_config.scanConcurrency, g_cancel,
                [](int visited, int pending) { PostMessageW(g_hwndMain, WM_APP_PROGRESS, visited, pending); });
            std::map<std::wstring, const Repository*> existingById;
            for (const Repository& repository : existing) existingById[repository.id] = &repository;
            std::vector<std::pair<std::wstring, const Repository*>> work;
            for (const std::wstring& path : paths) {
                const std::wstring id = GitScan::RepositoryId(path);
                const auto found = existingById.find(id);
                work.push_back({path, found == existingById.end() ? nullptr : found->second});
            }
            const std::vector<RepositoryUpdate> updates = UpdateMany(work);
            result->repositories.clear();
            std::set<std::wstring> foundIds;
            for (const RepositoryUpdate& update : updates) {
                result->repositories.push_back(update.repository);
                foundIds.insert(update.repository.id);
                CountResult(result->summary, update.status);
            }
            for (const Repository& old : existing) {
                if (foundIds.count(old.id) || old.updatedAt.empty()) continue;
                Repository missing = old;
                missing.available = false;
                missing.error = L"仓库路径当前不可用";
                result->repositories.push_back(missing);
                CountResult(result->summary, RepositoryUpdate::Unavailable);
            }
            result->lastDiscovery = TimestampWide();
            result->lastRefresh = result->lastDiscovery;
        }
    } else {
        std::vector<std::pair<std::wstring, const Repository*>> work;
        for (const Repository& repository : existing) work.push_back({repository.path, &repository});
        const std::vector<RepositoryUpdate> updates = UpdateMany(work);
        result->repositories.clear();
        for (const RepositoryUpdate& update : updates) {
            if (update.status != RepositoryUpdate::Unavailable || !update.repository.updatedAt.empty())
                result->repositories.push_back(update.repository);
            CountResult(result->summary, update.status);
        }
        result->lastRefresh = TimestampWide();
    }

    std::sort(result->repositories.begin(), result->repositories.end(), [](const Repository& left, const Repository& right) {
        return Lowercase(left.path) < Lowercase(right.path);
    });
    result->summary.durationMs = GetTickCount64() - started;
    if (!g_cancel.load() && result->error.empty()) {
        std::wstring saveError;
        if (!g_store.SaveIndex(result->repositories, result->roots, result->lastDiscovery, result->lastRefresh, saveError))
            result->error = saveError;
    }
    if (!PostMessageW(g_hwndMain, WM_APP_OPERATION_DONE, 0, reinterpret_cast<LPARAM>(result))) delete result;
}

bool DateToFileTime(int year, int month, int day, ULARGE_INTEGER& value) {
    SYSTEMTIME time = {};
    time.wYear = static_cast<WORD>(year);
    time.wMonth = static_cast<WORD>(month);
    time.wDay = static_cast<WORD>(day);
    FILETIME file = {};
    if (!SystemTimeToFileTime(&time, &file)) return false;
    value.LowPart = file.dwLowDateTime;
    value.HighPart = file.dwHighDateTime;
    return true;
}

std::wstring DateString(const SYSTEMTIME& time) {
    wchar_t buffer[16];
    _snwprintf_s(buffer, _countof(buffer), _TRUNCATE, L"%04u-%02u-%02u", time.wYear, time.wMonth, time.wDay);
    return buffer;
}

} // namespace

bool InitializeState(HWND hwnd) {
    g_hwndMain = hwnd;
    SYSTEMTIME now = {};
    GetLocalTime(&now);
    g_year = now.wYear;
    std::wstring notice;
    std::wstring error;
    std::wstring cacheNotice;
    if (!g_store.Initialize(cacheNotice, error)) {
        g_error = error;
        return false;
    }
    if (!LoadAppConfig(g_config, notice, error)) g_error = error;
    g_fontScale = g_config.fontSize;
    g_theme = g_config.theme;
    ApplyDarkMode(hwnd, g_theme == Theme::Dark);
    g_ui.InitColumnWidths();
    g_dataDirectory = g_store.directory();
    if (!cacheNotice.empty()) notice = notice.empty() ? cacheNotice : cacheNotice + L"；" + notice;
    if (!g_store.LoadIndex(g_repos, g_scanRoots, g_lastDiscovery, g_lastRefresh, error)) {
        g_error = error;
        g_repos.clear();
    }
    for (const Repository& repository : g_repos) g_selected[repository.id] = true;
    if (!GitScan::IsGitAvailable(g_gitVersion, error)) g_error = error;
    if (!notice.empty()) g_status = notice;
    // Compute yearTotal for all repos and sort sidebar (same as after refresh/discover)
    {
        std::wstring readError;
        for (Repository& repository : g_repos) {
            std::map<std::wstring, int> days;
            repository.yearTotal = g_store.LoadDays(repository.id, days, readError)
                ? static_cast<int>(std::count_if(days.begin(), days.end(), [prefix = FormatInteger(g_year) + L"-"](const auto& p) {
                      return p.first.compare(0, prefix.size(), prefix) == 0 && p.second > 0;
                  }))
                : 0;
        }
        SortReposByYearTotal();
    }
    RebuildContributions();
    return true;
}

void ShutdownState() {
    g_cancel.store(true);
    if (g_worker.joinable()) g_worker.join();
}

void StartRefresh() {
    if (g_loading) return;
    if (g_repos.empty()) { StartDiscovery(); return; }
    g_loading = true;
    g_operationKind = OperationKind::Refresh;
    g_error.clear();
    g_status = L"正在检查已知项目的 Git 引用…";
    g_cancel.store(false);
    if (g_worker.joinable()) g_worker.join();
    g_worker = std::thread(RunOperation, OperationKind::Refresh, g_repos, g_scanRoots, g_lastDiscovery, g_lastRefresh);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void StartDiscovery() {
    if (g_loading) return;
    g_loading = true;
    g_operationKind = OperationKind::Discover;
    g_error.clear();
    g_status = L"正在扫描本机磁盘，窗口可继续操作…";
    g_cancel.store(false);
    if (g_worker.joinable()) g_worker.join();
    g_worker = std::thread(RunOperation, OperationKind::Discover, g_repos, g_scanRoots, g_lastDiscovery, g_lastRefresh);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void HandleOperationDone(LPARAM resultPointer) {
    OperationResult* result = reinterpret_cast<OperationResult*>(resultPointer);
    if (!result) return;
    if (g_worker.joinable()) g_worker.join();
    g_loading = false;
    g_operationKind = OperationKind::None;
    if (!result->error.empty()) {
        g_error = result->error;
        g_status.clear();
    } else {
        std::unordered_map<std::wstring, bool> previous = g_selected;
        g_repos = result->repositories;
        g_scanRoots = result->roots;
        g_lastDiscovery = result->lastDiscovery;
        g_lastRefresh = result->lastRefresh;
        g_selected.clear();
        for (const Repository& repository : g_repos) {
            const auto selected = previous.find(repository.id);
            g_selected[repository.id] = selected == previous.end() ? true : selected->second;
        }
        const OperationSummary& summary = result->summary;
        g_status = (summary.kind == L"discover" ? L"发现完成：" : L"刷新完成：") +
            FormatInteger(summary.checked) + L" 个项目，新增 " + FormatInteger(summary.added) +
            L"，更新 " + FormatInteger(summary.updated) + L"，未变化 " + FormatInteger(summary.unchanged) +
            L"，不可用 " + FormatInteger(summary.unavailable) + L"，耗时 " + FormatDuration(summary.durationMs);
        g_ui.ClearDaySelection();
        RebuildContributions();
        // Compute yearTotal for all repos (independent of selection) and sort sidebar
        std::wstring readError;
        for (Repository& repository : g_repos) {
            std::map<std::wstring, int> days;
            repository.yearTotal = g_store.LoadDays(repository.id, days, readError)
                ? static_cast<int>(std::count_if(days.begin(), days.end(), [prefix = FormatInteger(g_year) + L"-"](const auto& p) {
                      return p.first.compare(0, prefix.size(), prefix) == 0 && p.second > 0;
                  }))
                : 0;
        }
        SortReposByYearTotal();
    }
    delete result;
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void HandleOperationProgress(WPARAM completed, LPARAM remaining) {
    if (!g_loading) return;
    if (g_operationKind == OperationKind::Discover)
        g_status = L"正在发现项目：已检查 " + FormatInteger(static_cast<int>(completed)) +
                   L" 个目录，待处理约 " + FormatInteger(static_cast<int>(remaining));
    else
        g_status = L"正在刷新项目：已完成 " + FormatInteger(static_cast<int>(completed)) +
                   L" 个，剩余 " + FormatInteger(static_cast<int>(remaining));
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void RebuildContributions() {
    ContributionData next;
    next.year = g_year;
    std::map<std::wstring, DayEntry> byDate;
    std::map<std::wstring, int> totals;
    std::wstring readError;
    for (const Repository& repository : g_repos) {
        const auto selected = g_selected.find(repository.id);
        if (selected == g_selected.end() || !selected->second) continue;
        std::map<std::wstring, int> days;
        if (!g_store.LoadDays(repository.id, days, readError)) continue;
        const std::wstring prefix = FormatInteger(g_year) + L"-";
        for (const auto& pair : days) {
            if (pair.first.compare(0, prefix.size(), prefix) != 0) continue;
            DayEntry& day = byDate[pair.first];
            day.date = pair.first;
            day.count += pair.second;
            day.details.push_back({repository.name, pair.second});
            totals[repository.id] += pair.second;
        }
    }

    ULARGE_INTEGER first = {};
    DateToFileTime(g_year, 1, 1, first);
    SYSTEMTIME firstTime = {};
    FILETIME firstFile = {first.LowPart, first.HighPart};
    FileTimeToSystemTime(&firstFile, &firstTime);
    first.QuadPart -= static_cast<unsigned long long>(firstTime.wDayOfWeek) * 864000000000ULL;
    ULARGE_INTEGER last = {};
    DateToFileTime(g_year, 12, 31, last);
    SYSTEMTIME lastTime = {};
    FILETIME lastFile = {last.LowPart, last.HighPart};
    FileTimeToSystemTime(&lastFile, &lastTime);
    last.QuadPart += static_cast<unsigned long long>(6 - lastTime.wDayOfWeek) * 864000000000ULL;
    for (ULARGE_INTEGER cursor = first; cursor.QuadPart <= last.QuadPart; cursor.QuadPart += 864000000000ULL) {
        FILETIME file = {cursor.LowPart, cursor.HighPart};
        SYSTEMTIME time = {};
        FileTimeToSystemTime(&file, &time);
        const std::wstring date = DateString(time);
        DayEntry day;
        const auto found = byDate.find(date);
        if (found != byDate.end()) day = found->second;
        else day.date = date;
        day.inYear = time.wYear == g_year;
        next.days.push_back(day);
        if (day.inYear) {
            next.total += day.count;
            if (day.count > 0) ++next.activeDays;
        }
    }
    for (const Repository& repository : g_repos) {
        const int count = totals[repository.id];
        if (count > 0) next.repoStats.push_back({repository.id, repository.name, count});
    }
    std::sort(next.repoStats.begin(), next.repoStats.end(), [](const ContributionData::RepoStat& left,
                                                                const ContributionData::RepoStat& right) {
        return left.count > right.count;
    });
    g_contributionData = next;
}

void SortReposByYearTotal() {
    std::sort(g_repos.begin(), g_repos.end(), [](const Repository& left, const Repository& right) {
        if (left.yearTotal != right.yearTotal) return left.yearTotal > right.yearTotal;
        return Lowercase(left.path) < Lowercase(right.path);
    });
}

void LoadDayCommits(int dayIndex) {
    if (dayIndex < 0 || dayIndex >= static_cast<int>(g_contributionData.days.size())) return;
    DayEntry& day = g_contributionData.days[dayIndex];
    if (day.commitsLoaded) return;
    day.commitsLoaded = true;
    day.commits.clear();
    day.commitError.clear();

    std::wstring readError;
    for (const Repository& repository : g_repos) {
        const auto selected = g_selected.find(repository.id);
        if (selected == g_selected.end() || !selected->second) continue;
        std::vector<DayEntry::CommitEntry> commits;
        readError.clear();
        if (!g_store.LoadCommitsForDate(repository.id, day.date, commits, readError)) {
            if (day.commitError.empty()) day.commitError = readError;
            continue;
        }
        for (auto& commit : commits) {
            commit.repoName = repository.name;
            commit.repoPath = repository.path;
            day.commits.push_back(commit);
        }
    }
    std::sort(day.commits.begin(), day.commits.end(), [](const DayEntry::CommitEntry& a, const DayEntry::CommitEntry& b) {
        if (a.time != b.time) return a.time > b.time;
        return Lowercase(a.repoName) < Lowercase(b.repoName);
    });
}

std::vector<size_t> VisibleRepositoryIndices() {
    std::vector<size_t> result;
    const std::wstring query = Lowercase(g_query);
    for (size_t index = 0; index < g_repos.size(); ++index) {
        const Repository& repository = g_repos[index];
        if (query.empty() || Lowercase(repository.name).find(query) != std::wstring::npos ||
            Lowercase(repository.path).find(query) != std::wstring::npos) result.push_back(index);
    }
    return result;
}

void ToggleRepository(const std::wstring& id) {
    g_selected[id] = !g_selected[id];
    g_ui.ClearDaySelection();
    RebuildContributions();
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void SelectAllVisible(bool selected) {
    for (size_t index : VisibleRepositoryIndices()) g_selected[g_repos[index].id] = selected;
    g_ui.ClearDaySelection();
    RebuildContributions();
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void SetSearchQuery(const std::wstring& query) {
    g_query = query;
    g_repoScroll = 0;
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

int SelectedRepositoryCount() {
    int count = 0;
    for (const auto& pair : g_selected) if (pair.second) ++count;
    return count;
}

void ChangeYear(int delta) {
    const int next = g_year + delta;
    if (next < 1970 || next > 2100) return;
    g_year = next;
    g_ui.ClearDaySelection();
    RebuildContributions();
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void AdjustFontSize(int delta) {
    const double step = 0.1;
    double newSize = g_config.fontSize + delta * step;
    if (newSize < g_config.minFontSize) newSize = g_config.minFontSize;
    if (newSize > g_config.maxFontSize) newSize = g_config.maxFontSize;
    if (newSize != g_config.fontSize) {
        g_config.fontSize = newSize;
        g_fontScale = newSize;  // Update the global scale factor too
        g_ui.InitColumnWidths();
        RefreshSearchControlScale();
        std::wstring saveError;
        SaveAppConfig(g_config, saveError);  // Persist the new font size
    }
}

void ApplyDarkMode(HWND hwnd, bool dark) {
    HMODULE library = LoadLibraryW(L"dwmapi.dll");
    if (!library) return;
    using SetAttribute = HRESULT(WINAPI*)(HWND, DWORD, LPCVOID, DWORD);
    SetAttribute setAttribute = reinterpret_cast<SetAttribute>(GetProcAddress(library, "DwmSetWindowAttribute"));
    if (setAttribute) {
        BOOL enabled = dark ? TRUE : FALSE;
        setAttribute(hwnd, 20, &enabled, sizeof(enabled));
    }
    FreeLibrary(library);
}

void ToggleTheme() {
    g_theme = g_theme == Theme::Light ? Theme::Dark : Theme::Light;
    g_config.theme = g_theme;
    ApplyDarkMode(g_hwndMain, g_theme == Theme::Dark);
    std::wstring saveError;
    SaveAppConfig(g_config, saveError);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}
