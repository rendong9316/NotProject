#include "cache_store.h"

#include "json.h"
#include "platform.h"

#include <windows.h>

namespace {

std::wstring JsonWide(const Json& value) { return value.isString() ? Utf8ToWide(value.string()) : L""; }
Json JsonText(const std::wstring& value) { return Json(WideToUtf8(value)); }

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

bool CacheStore::SaveDays(const Repository& repository, const std::map<std::wstring, int>& days, std::wstring& error) const {
    Json root = Json::Object();
    root["version"] = Json(1.0);
    root["id"] = JsonText(repository.id);
    root["path"] = JsonText(repository.path);
    root["updatedAt"] = JsonText(repository.updatedAt);
    Json values = Json::Object();
    for (const auto& pair : days) values[WideToUtf8(pair.first)] = Json(static_cast<double>(pair.second));
    root["days"] = values;
    return WriteUtf8FileAtomic(RepositoryFile(repository.id), root.Serialize() + "\n", error);
}

bool CacheStore::HasDays(const std::wstring& repositoryId) const { return PathExists(RepositoryFile(repositoryId)); }

bool LoadAppConfig(AppConfig& config, std::wstring& notice, std::wstring& error) {
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
    config.includeAllAuthors = root.get("includeAllAuthors").boolean(false);
    config.maxScanDepth = root.get("maxScanDepth").integer(10);
    config.scanConcurrency = root.get("scanConcurrency").integer(8);
    config.gitConcurrency = root.get("gitConcurrency").integer(6);
    config.maxScanDepth = std::max(1, std::min(64, config.maxScanDepth));
    config.scanConcurrency = std::max(1, std::min(32, config.scanConcurrency));
    config.gitConcurrency = std::max(1, std::min(16, config.gitConcurrency));
    config.fontSize = root.get("fontSize").number(config.fontSize);
    for (const Json& item : root.get("authors").array()) if (item.isString()) config.authors.push_back(Utf8ToWide(item.string()));
    for (const Json& item : root.get("scanRoots").array()) if (item.isString()) config.scanRoots.push_back(Utf8ToWide(item.string()));
    if (config.authors.empty()) config.includeAllAuthors = true;
    return true;
}

bool SaveAppConfig(const AppConfig& config, std::wstring& error) {
    std::wstring path = JoinPath(ApplicationDataDirectory(), L"config.json");
    Json root = Json::Object();
    root["scanAllDrives"] = Json(config.scanAllDrives);
    root["includeAllAuthors"] = Json(config.includeAllAuthors);
    root["maxScanDepth"] = Json(config.maxScanDepth);
    root["scanConcurrency"] = Json(config.scanConcurrency);
    root["gitConcurrency"] = Json(config.gitConcurrency);
    root["fontSize"] = Json(config.fontSize);
    Json authors = Json::Array();
    for (const auto& a : config.authors) authors.push(JsonText(a));
    root["authors"] = authors;
    Json scanRoots = Json::Array();
    for (const auto& r : config.scanRoots) scanRoots.push(JsonText(r));
    root["scanRoots"] = scanRoots;
    return WriteUtf8FileAtomic(path, root.Serialize() + "\n", error);
}
