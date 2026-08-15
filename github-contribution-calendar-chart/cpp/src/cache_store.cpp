#include "cache_store.h"

#include "json.h"
#include "platform.h"

#include <windows.h>
#include <algorithm>
#include <set>

namespace {

std::wstring JsonWide(const Json& value) { return value.isString() ? Utf8ToWide(value.string()) : L""; }
Json JsonText(const std::wstring& value) { return Json(WideToUtf8(value)); }

std::wstring Trimmed(const std::wstring& value) {
    const size_t first = value.find_first_not_of(L" \t\r\n");
    if (first == std::wstring::npos) return L"";
    const size_t last = value.find_last_not_of(L" \t\r\n");
    return value.substr(first, last - first + 1);
}

bool NormalizeUnique(std::vector<std::wstring>& values) {
    std::vector<std::wstring> normalized;
    std::set<std::wstring> seen;
    normalized.reserve(values.size());
    for (const std::wstring& value : values) {
        const std::wstring trimmed = Trimmed(value);
        if (trimmed.empty() || !seen.insert(Lowercase(trimmed)).second) continue;
        normalized.push_back(trimmed);
    }
    if (normalized == values) return false;
    values.swap(normalized);
    return true;
}

bool NormalizeConfigLists(AppConfig& config) {
    const bool authorsChanged = NormalizeUnique(config.authors);
    const bool rootsChanged = NormalizeUnique(config.scanRoots);
    return authorsChanged || rootsChanged;
}

Json RepositoryToJson(const Repository& repository) {
    Json value = Json::Object();
    value["id"] = JsonText(repository.id);
    value["name"] = JsonText(repository.name);
    value["path"] = JsonText(repository.path);
    value["fingerprint"] = repository.fingerprint.empty() ? Json() : JsonText(repository.fingerprint);
    value["filterSignature"] = repository.filterSignature.empty() ? Json() : JsonText(repository.filterSignature);
    value["available"] = Json(repository.available);
    value["checkedAt"] = repository.checkedAt.empty() ? Json() : JsonText(repository.checkedAt);
    value["updatedAt"] = repository.updatedAt.empty() ? Json() : JsonText(repository.updatedAt);
    value["error"] = repository.error.empty() ? Json() : JsonText(repository.error);
    value["historySaveTime"] = repository.historySaveTime.empty() ? Json() : JsonText(repository.historySaveTime);
    return value;
}

Repository JsonToRepository(const Json& value) {
    Repository repository;
    repository.id = JsonWide(value.get("id"));
    repository.name = JsonWide(value.get("name"));
    repository.path = JsonWide(value.get("path"));
    repository.fingerprint = JsonWide(value.get("fingerprint"));
    repository.filterSignature = JsonWide(value.get("filterSignature"));
    repository.checkedAt = JsonWide(value.get("checkedAt"));
    repository.updatedAt = JsonWide(value.get("updatedAt"));
    repository.error = JsonWide(value.get("error"));
    repository.historySaveTime = JsonWide(value.get("historySaveTime"));
    repository.available = value.get("available").boolean(false);
    return repository;
}

bool ReadJson(const std::wstring& path, Json& value, std::wstring& error) {
    std::string source;
    if (!ReadUtf8File(path, source, error)) return false;
    std::string parseError;
    if (!Json::Parse(source, value, parseError)) {
        error = L"JSON 损坏：" + path + L"（" + Utf8ToWide(parseError) + L"）";
        return false;
    }
    return true;
}

} // namespace

CacheStore::CacheStore() : directory_(ApplicationDataDirectory()) {}

bool CacheStore::Initialize(std::wstring& notice, std::wstring& error) {
    if (!EnsureDirectory(directory_) || !EnsureDirectory(JoinPath(directory_, L"repositories"))) {
        error = L"无法创建数据目录：" + directory_;
        return false;
    }
    if (!PathExists(JoinPath(directory_, L"index.json"))) MigrateDevelopmentCache(notice);
    return true;
}

bool CacheStore::MigrateDevelopmentCache(std::wstring& notice) const {
    const std::wstring projectIndex = FindProjectFile(L"data\\index.json");
    if (projectIndex.empty()) return false;
    const std::wstring sourceData = ParentPath(projectIndex);
    if (!CopyFileW(projectIndex.c_str(), JoinPath(directory_, L"index.json").c_str(), TRUE)) return false;
    CopyDirectoryJson(JoinPath(sourceData, L"repositories"), JoinPath(directory_, L"repositories"));
    notice = L"已迁移 Web 版缓存到 " + directory_;
    return true;
}

bool CacheStore::LoadIndex(std::vector<Repository>& repositories, std::vector<std::wstring>& scanRoots,
                           std::wstring& lastDiscovery, std::wstring& lastRefresh, std::wstring& error) const {
    repositories.clear();
    scanRoots.clear();
    const std::wstring path = JoinPath(directory_, L"index.json");
    if (!PathExists(path)) return true;
    Json root;
    if (!ReadJson(path, root, error) || !root.isObject()) return false;
    if (root.get("version").integer(0) != 1) {
        error = L"缓存版本不受支持，请执行“发现项目”重建缓存。";
        return false;
    }
    for (const Json& item : root.get("scanRoots").array()) {
        if (item.isString()) scanRoots.push_back(Utf8ToWide(item.string()));
    }
    for (const Json& item : root.get("repositories").array()) {
        if (!item.isObject()) continue;
        Repository repository = JsonToRepository(item);
        if (!repository.id.empty() && !repository.path.empty()) repositories.push_back(repository);
    }
    lastDiscovery = JsonWide(root.get("lastDiscoveryAt"));
    lastRefresh = JsonWide(root.get("lastRefreshAt"));
    return true;
}

bool CacheStore::SaveIndex(const std::vector<Repository>& repositories, const std::vector<std::wstring>& scanRoots,
                           const std::wstring& lastDiscovery, const std::wstring& lastRefresh, std::wstring& error) const {
    Json root = Json::Object();
    root["version"] = Json(1.0);
    Json roots = Json::Array();
    for (const std::wstring& scanRoot : scanRoots) roots.push(JsonText(scanRoot));
    root["scanRoots"] = roots;
    root["lastDiscoveryAt"] = lastDiscovery.empty() ? Json() : JsonText(lastDiscovery);
    root["lastRefreshAt"] = lastRefresh.empty() ? Json() : JsonText(lastRefresh);
    Json items = Json::Array();
    for (const Repository& repository : repositories) items.push(RepositoryToJson(repository));
    root["repositories"] = items;
    return WriteUtf8FileAtomic(JoinPath(directory_, L"index.json"), root.Serialize() + "\n", error);
}

std::wstring CacheStore::RepositoryFile(const std::wstring& id) const {
    return JoinPath(JoinPath(directory_, L"repositories"), id + L".json");
}

bool CacheStore::LoadDays(const std::wstring& repositoryId, std::map<std::wstring, int>& days, std::wstring& error) const {
    days.clear();
    Json root;
    if (!ReadJson(RepositoryFile(repositoryId), root, error)) return false;
    const Json& values = root.get("days");
    if (!values.isObject()) return true;
    for (const auto& pair : values.object()) {
        if (pair.second.isNumber()) days[Utf8ToWide(pair.first)] = pair.second.integer();
    }
    return true;
}

bool CacheStore::LoadCommitsForDate(const std::wstring& repositoryId, const std::wstring& date,
                                    std::vector<DayEntry::CommitEntry>& commits,
                                    std::wstring& error) const {
    commits.clear();
    Json root;
    if (!ReadJson(RepositoryFile(repositoryId), root, error)) return false;
    const Json& values = root.get("commits");
    if (!values.isArray()) return true;
    for (const Json& item : values.array()) {
        if (!item.isObject()) continue;
        DayEntry::CommitEntry entry;
        entry.hash = JsonWide(item.get("hash"));
        entry.date = JsonWide(item.get("date"));
        entry.time = JsonWide(item.get("time"));
        entry.message = JsonWide(item.get("message"));
        entry.author = JsonWide(item.get("author"));
        if (entry.date == date) commits.push_back(entry);
    }
    return true;
}

bool CacheStore::LoadAllCommits(const std::wstring& repositoryId,
                                std::vector<DayEntry::CommitEntry>& commits,
                                std::wstring& error) const {
    commits.clear();
    Json root;
    if (!ReadJson(RepositoryFile(repositoryId), root, error)) return false;
    const Json& values = root.get("commits");
    if (!values.isArray()) return true;
    for (const Json& item : values.array()) {
        if (!item.isObject()) continue;
        DayEntry::CommitEntry entry;
        entry.hash = JsonWide(item.get("hash"));
        entry.date = JsonWide(item.get("date"));
        entry.time = JsonWide(item.get("time"));
        entry.message = JsonWide(item.get("message"));
        entry.author = JsonWide(item.get("author"));
        commits.push_back(std::move(entry));
    }
    return true;
}

bool CacheStore::SaveHistory(const Repository& repository, const std::map<std::wstring, int>& days,
                             const std::vector<DayEntry::CommitEntry>& commits,
                             std::wstring& error, std::wstring* saveTime) const {
    const std::wstring filePath = RepositoryFile(repository.id);
    Json root = Json::Object();
    root["version"] = Json(2.0);
    root["historyVersion"] = Json(2.0);
    root["id"] = JsonText(repository.id);
    root["path"] = JsonText(repository.path);
    root["updatedAt"] = JsonText(repository.updatedAt);

    Json values = Json::Object();
    for (const auto& pair : days) values[WideToUtf8(pair.first)] = Json(static_cast<double>(pair.second));
    root["days"] = values;

    Json arr = Json::Array();
    for (const DayEntry::CommitEntry& commit : commits) {
        Json item = Json::Object();
        item["hash"] = commit.hash.empty() ? Json() : JsonText(commit.hash);
        item["date"] = JsonText(commit.date);
        item["time"] = commit.time.empty() ? Json() : JsonText(commit.time);
        item["message"] = JsonText(commit.message);
        item["author"] = commit.author.empty() ? Json() : JsonText(commit.author);
        arr.push(item);
    }
    root["commits"] = arr;
    // Write the file first, then capture its mtime for future incremental checks.
    bool success = WriteUtf8FileAtomic(filePath, root.Serialize() + "\n", error);
    if (success && saveTime) {
        HANDLE h = CreateFileW(filePath.c_str(), GENERIC_READ,
                               FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                               nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (h != INVALID_HANDLE_VALUE) {
            FILETIME ft = {};
            if (GetFileTime(h, nullptr, nullptr, &ft)) {
                wchar_t buf[32] = {};
                _snwprintf_s(buf, _countof(buf), _TRUNCATE, L"%08x%08x",
                             ft.dwHighDateTime, ft.dwLowDateTime);
                *saveTime = buf;
            }
            CloseHandle(h);
        }
    }
    return success;
}

bool CacheStore::HasCompleteHistory(const std::wstring& repositoryId) const {
    const std::wstring path = RepositoryFile(repositoryId);
    if (!PathExists(path)) return false;
    Json root;
    std::wstring error;
    return ReadJson(path, root, error) && root.get("historyVersion").integer(0) >= 2 &&
           root.get("days").isObject() && root.get("commits").isArray();
}

bool LoadAppConfig(AppConfig& config, std::wstring& notice, std::wstring& error) {
    config = AppConfig();
    std::wstring path = JoinPath(ApplicationDataDirectory(), L"config.json");
    if (!PathExists(path)) {
        const std::wstring projectConfig = FindProjectFile(L"config.json");
        std::string content;
        if (!projectConfig.empty() && ReadUtf8File(projectConfig, content, error) &&
            WriteUtf8FileAtomic(path, content, error)) {
            notice = L"已将配置迁移到 " + path;
        } else if (!projectConfig.empty()) {
            return false;
        } else {
            path.clear();
        }
    }
    if (path.empty()) {
        notice = L"未找到 config.json，正在使用默认配置（统计全部作者）。";
        config.includeAllAuthors = true;
        return true;
    }
    Json root;
    if (!ReadJson(path, root, error)) return false;
    config.scanAllDrives = root.get("scanAllDrives").boolean(true);
    const int loadedSchemaVersion = root.get("schemaVersion").integer(1);
    config.schemaVersion = 2;
    config.includeAllAuthors = root.get("includeAllAuthors").boolean(false);
    config.maxScanDepth = root.get("maxScanDepth").integer(10);
    config.scanConcurrency = root.get("scanConcurrency").integer(8);
    config.gitConcurrency = root.get("gitConcurrency").integer(6);
    config.maxScanDepth = std::max(1, std::min(64, config.maxScanDepth));
    config.scanConcurrency = std::max(1, std::min(32, config.scanConcurrency));
    config.gitConcurrency = std::max(1, std::min(16, config.gitConcurrency));
    const double loadedFontSize = root.get("fontSize").number(config.fontSize);
    config.fontSize = std::max(config.minFontSize, std::min(config.maxFontSize, loadedFontSize));
    config.theme = root.get("theme").integer(0) == 1 ? Theme::Dark : Theme::Light;
    for (const Json& item : root.get("authors").array()) if (item.isString()) config.authors.push_back(Utf8ToWide(item.string()));
    for (const Json& item : root.get("scanRoots").array()) if (item.isString()) config.scanRoots.push_back(Utf8ToWide(item.string()));
    // Load column widths
    const Json& colWidthsJson = root.get("columnWidths");
    if (colWidthsJson.isArray()) {
        config.columnWidths.clear();
        for (const Json& item : colWidthsJson.array()) {
            if (item.isNumber()) config.columnWidths.push_back(item.integer());
        }
    }
    // Load sidebar width
    config.sidebarWidth = root.get("sidebarWidth").integer(250);
    config.sidebarWidth = std::max(190, std::min(600, config.sidebarWidth));
    const Json& ai = root.get("ai");
    if (ai.isObject()) {
        config.aiEnabled = ai.get("enabled").boolean(config.aiEnabled);
        if (ai.get("provider").isString()) config.aiProvider = Utf8ToWide(ai.get("provider").string());
        if (ai.get("baseUrl").isString()) config.aiBaseUrl = Utf8ToWide(ai.get("baseUrl").string());
        if (ai.get("model").isString()) config.aiModel = Utf8ToWide(ai.get("model").string());
        if (ai.get("apiKey").isString()) config.aiApiKey = Utf8ToWide(ai.get("apiKey").string());
        config.aiMaxTokens = std::max(256, std::min(16384, ai.get("maxTokens").integer(config.aiMaxTokens)));
        config.aiContextWindowTokens = std::max(4096, ai.get("contextWindowTokens").integer(config.aiContextWindowTokens));
        config.aiTemperature = std::max(0.0, std::min(2.0, ai.get("temperature").number(config.aiTemperature)));
        config.aiPrivacyConsent = ai.get("privacyConsent").boolean(config.aiPrivacyConsent);
        config.aiRedactPaths = ai.get("redactPaths").boolean(config.aiRedactPaths);
        config.aiPersistConversations = ai.get("persistConversations").boolean(config.aiPersistConversations);
    }
    const bool normalized = NormalizeConfigLists(config) || config.fontSize != loadedFontSize || loadedSchemaVersion != 2;
    if (config.authors.empty()) config.includeAllAuthors = true;
    if (normalized) {
        std::wstring saveError;
        if (SaveAppConfig(config, saveError)) {
            const std::wstring message = L"已清理配置中的重复作者或扫描目录";
            notice = notice.empty() ? message : notice + L"；" + message;
        } else if (error.empty()) {
            error = saveError;
        }
    }
    return true;
}

bool SaveAppConfig(const AppConfig& config, std::wstring& error) {
    AppConfig normalized = config;
    NormalizeConfigLists(normalized);
    std::wstring path = JoinPath(ApplicationDataDirectory(), L"config.json");
    Json root = Json::Object();
    root["schemaVersion"] = Json(2);
    root["scanAllDrives"] = Json(normalized.scanAllDrives);
    root["includeAllAuthors"] = Json(normalized.includeAllAuthors);
    root["maxScanDepth"] = Json(normalized.maxScanDepth);
    root["scanConcurrency"] = Json(normalized.scanConcurrency);
    root["gitConcurrency"] = Json(normalized.gitConcurrency);
    root["fontSize"] = Json(normalized.fontSize);
    root["theme"] = Json(static_cast<double>(normalized.theme == Theme::Dark ? 1 : 0));
    Json authors = Json::Array();
    for (const auto& a : normalized.authors) authors.push(JsonText(a));
    root["authors"] = authors;
    Json scanRoots = Json::Array();
    for (const auto& r : normalized.scanRoots) scanRoots.push(JsonText(r));
    root["scanRoots"] = scanRoots;
    // Save column widths
    Json colWidthsJson = Json::Array();
    for (const int w : normalized.columnWidths) colWidthsJson.push(Json(w));
    root["columnWidths"] = colWidthsJson;
    root["sidebarWidth"] = Json(normalized.sidebarWidth);
    Json ai = Json::Object();
    ai["enabled"] = Json(normalized.aiEnabled);
    ai["provider"] = Json(WideToUtf8(normalized.aiProvider));
    ai["baseUrl"] = Json(WideToUtf8(normalized.aiBaseUrl));
    ai["model"] = Json(WideToUtf8(normalized.aiModel));
    ai["apiKey"] = Json(WideToUtf8(normalized.aiApiKey));
    ai["maxTokens"] = Json(normalized.aiMaxTokens);
    ai["contextWindowTokens"] = Json(normalized.aiContextWindowTokens);
    ai["temperature"] = Json(normalized.aiTemperature);
    ai["privacyConsent"] = Json(normalized.aiPrivacyConsent);
    ai["redactPaths"] = Json(normalized.aiRedactPaths);
    ai["persistConversations"] = Json(normalized.aiPersistConversations);
    root["ai"] = ai;
    return WriteUtf8FileAtomic(path, root.Serialize() + "\n", error);
}
