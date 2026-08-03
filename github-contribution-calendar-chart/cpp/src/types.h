#pragma once

#include <windows.h>
#include <map>
#include <string>
#include <vector>

enum class Theme { Light, Dark };
enum class OperationKind { None, Refresh, Discover };

struct AppConfig {
    bool scanAllDrives = true;
    bool includeAllAuthors = false;
    int maxScanDepth = 10;
    int scanConcurrency = 8;
    int gitConcurrency = 6;
    std::vector<std::wstring> scanRoots;
    std::vector<std::wstring> authors;
    double fontSize = 1.0;  // Default font scale factor
    double minFontSize = 0.5;  // Minimum scale
    double maxFontSize = 3.0;  // Maximum scale (was 3.0, reduce to prevent UI overflow)
    std::vector<int> columnWidths;  // Detail panel column widths
    Theme theme = Theme::Light;
};

struct Repository {
    std::wstring id;
    std::wstring name;
    std::wstring path;
    std::wstring fingerprint;
    std::wstring filterSignature;
    std::wstring checkedAt;
    std::wstring updatedAt;
    std::wstring error;
    std::wstring historySaveTime;  // FILETIME string recorded when history was last saved
    bool available = false;
    int yearTotal = 0;
};

struct DayEntry {
    std::wstring date;
    int count = 0;
    bool inYear = true;
    struct Detail { std::wstring repoName; int count = 0; };
    struct CommitEntry {
        std::wstring hash;
        std::wstring date;
        std::wstring time;
        std::wstring message;
        std::wstring author;
        std::wstring repoName;
        std::wstring repoPath;
    };
    std::vector<Detail> details;
    std::vector<CommitEntry> commits;
    bool commitsLoaded = false;
    std::wstring commitError;
};

struct ContributionData {
    int year = 0;
    std::vector<DayEntry> days;
    int total = 0;
    int activeDays = 0;
    struct RepoStat { std::wstring id; std::wstring name; int count = 0; };
    std::vector<RepoStat> repoStats;
};

struct OperationSummary {
    std::wstring kind;
    int checked = 0;
    int added = 0;
    int updated = 0;
    int unchanged = 0;
    int unavailable = 0;
    unsigned long long durationMs = 0;
};
