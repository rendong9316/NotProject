#pragma once

#include <windows.h>
#include <map>
#include <string>
#include <vector>

struct AppConfig {
    bool scanAllDrives = true;
    bool includeAllAuthors = false;
    int maxScanDepth = 10;
    int scanConcurrency = 8;
    int gitConcurrency = 6;
    std::vector<std::wstring> scanRoots;
    std::vector<std::wstring> authors;
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
    bool available = false;
};

struct DayEntry {
    std::wstring date;
    int count = 0;
    bool inYear = true;
    struct Detail { std::wstring repoName; int count = 0; };
    std::vector<Detail> details;
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

enum class Theme { Light, Dark };
enum class OperationKind { None, Refresh, Discover };
