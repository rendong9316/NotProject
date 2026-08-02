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
    bool SaveDays(const Repository& repository, const std::map<std::wstring, int>& days, std::wstring& error) const;
    bool LoadCommits(const std::wstring& repositoryId, std::vector<DayEntry::CommitEntry>& commits, std::wstring& error) const;
    bool SaveCommits(const Repository& repository, const std::vector<DayEntry::CommitEntry>& commits, std::wstring& error) const;
    bool HasDays(const std::wstring& repositoryId) const;

    const std::wstring& directory() const { return directory_; }

private:
    std::wstring RepositoryFile(const std::wstring& id) const;
    bool MigrateDevelopmentCache(std::wstring& notice) const;

    std::wstring directory_;
};

bool LoadAppConfig(AppConfig& config, std::wstring& notice, std::wstring& error);
bool SaveAppConfig(const AppConfig& config, std::wstring& error);
