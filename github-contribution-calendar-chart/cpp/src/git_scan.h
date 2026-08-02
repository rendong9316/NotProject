#pragma once

#include "types.h"
#include <atomic>
#include <functional>
#include <map>
#include <string>
#include <vector>

class GitScan {
public:
    using Progress = std::function<void(int completed, int total)>;

    static std::wstring RepositoryId(const std::wstring& path);
    static std::wstring FilterSignature(const AppConfig& config);
    static bool ReadFingerprint(const std::wstring& path, std::wstring& fingerprint, std::wstring& error);
    static bool CollectHistory(const std::wstring& path, const AppConfig& config,
                               std::map<std::wstring, int>& days,
                               std::vector<DayEntry::CommitEntry>& commits,
                               std::wstring& error);
    static std::vector<std::wstring> FindRepositories(const std::vector<std::wstring>& roots,
                                                       int maxDepth, int concurrency,
                                                       std::atomic<bool>& cancel,
                                                       const Progress& progress);
    static bool IsGitAvailable(std::wstring& version, std::wstring& error);

private:
    static bool RunGit(const std::vector<std::wstring>& arguments, std::string& output,
                       std::wstring& error, unsigned long timeoutMs = 120000);
};
