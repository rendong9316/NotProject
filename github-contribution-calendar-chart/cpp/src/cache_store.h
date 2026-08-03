#pragma once

#include "types.h"
#include <map>
#include <string>
#include <vector>

class CacheStore {
public:
    CacheStore();

    bool Initialize(std::wstring& notice, std::wstring& error);
    bool LoadIndex(std::vector<Repository>& repositories, std::vector<std::wstring>& scanRoots,
                   std::wstring& lastDiscovery, std::wstring& lastRefresh, std::wstring& error) const;
    bool SaveIndex(const std::vector<Repository>& repositories, const std::vector<std::wstring>& scanRoots,
                   const std::wstring& lastDiscovery, const std::wstring& lastRefresh, std::wstring& error) const;
    bool LoadDays(const std::wstring& repositoryId, std::map<std::wstring, int>& days, std::wstring& error) const;
    bool LoadCommitsForDate(const std::wstring& repositoryId, const std::wstring& date,
                            std::vector<DayEntry::CommitEntry>& commits, std::wstring& error) const;
    bool SaveHistory(const Repository& repository, const std::map<std::wstring, int>& days,
                     const std::vector<DayEntry::CommitEntry>& commits,
                     std::wstring& error, std::wstring* saveTime = nullptr) const;
    bool HasCompleteHistory(const std::wstring& repositoryId) const;
    std::wstring RepositoryFile(const std::wstring& id) const;

    const std::wstring& directory() const { return directory_; }

private:
    bool MigrateDevelopmentCache(std::wstring& notice) const;

    std::wstring directory_;
};

bool LoadAppConfig(AppConfig& config, std::wstring& notice, std::wstring& error);
bool SaveAppConfig(const AppConfig& config, std::wstring& error);
