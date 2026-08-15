#include "ai_tools.h"

#include "cache_store.h"
#include "platform.h"

#include <algorithm>
#include <array>
#include <ctime>
#include <cwctype>
#include <map>
#include <set>

namespace {

bool IsSelected(const Repository& repository, const AnalyticsContext& context) {
    const auto found = context.selected.find(repository.id);
    return repository.available && found != context.selected.end() && found->second;
}

std::wstring YearPrefix(int year) { return std::to_wstring(year) + L"-"; }

Json ErrorResult(const std::wstring& message) {
    Json result = Json::Object();
    result["ok"] = Json(false);
    result["error"] = Json(WideToUtf8(message));
    return result;
}

Json OkResult() {
    Json result = Json::Object();
    result["ok"] = Json(true);
    return result;
}

int ArgYear(const Json& arguments, const AnalyticsContext& context) {
    return arguments.get("year").integer(context.activeYear);
}

bool LoadSelectedDays(const AnalyticsContext& context,
                      std::map<std::wstring, int>& total,
                      std::map<std::wstring, std::map<std::wstring, int>>& byRepository,
                      std::wstring& error) {
    CacheStore store;
    std::wstring notice;
    if (!store.Initialize(notice, error)) return false;
    for (const Repository& repository : context.repositories) {
        if (!IsSelected(repository, context)) continue;
        std::map<std::wstring, int> days;
        if (!store.LoadDays(repository.id, days, error)) return false;
        byRepository[repository.name] = days;
        for (const auto& pair : days) total[pair.first] += pair.second;
    }
    return true;
}

bool LoadSelectedCommits(const AnalyticsContext& context,
                         std::vector<DayEntry::CommitEntry>& all,
                         std::wstring& error) {
    CacheStore store;
    std::wstring notice;
    if (!store.Initialize(notice, error)) return false;
    for (const Repository& repository : context.repositories) {
        if (!IsSelected(repository, context)) continue;
        std::vector<DayEntry::CommitEntry> commits;
        if (!store.LoadAllCommits(repository.id, commits, error)) return false;
        for (auto& commit : commits) {
            commit.repoName = repository.name;
            all.push_back(std::move(commit));
        }
    }
    return true;
}

int Weekday(const std::wstring& date) {
    if (date.size() != 10) return -1;
    std::tm value = {};
    value.tm_year = std::stoi(date.substr(0, 4)) - 1900;
    value.tm_mon = std::stoi(date.substr(5, 2)) - 1;
    value.tm_mday = std::stoi(date.substr(8, 2));
    value.tm_isdst = -1;
    if (std::mktime(&value) == -1) return -1;
    return value.tm_wday;
}

int DayOfYear(const std::wstring& date) {
    if (date.size() != 10) return 0;
    std::tm value = {};
    value.tm_year = std::stoi(date.substr(0, 4)) - 1900;
    value.tm_mon = std::stoi(date.substr(5, 2)) - 1;
    value.tm_mday = std::stoi(date.substr(8, 2));
    value.tm_isdst = -1;
    if (std::mktime(&value) == -1) return 0;
    return value.tm_yday;
}

std::wstring Lower(const std::wstring& value) {
    std::wstring result = value;
    std::transform(result.begin(), result.end(), result.begin(), [](wchar_t ch) { return std::towlower(ch); });
    return result;
}

const Repository* FindRepository(const std::wstring& query, const AnalyticsContext& context) {
    const std::wstring loweredQuery = Lower(query);
    const Repository* best = nullptr;
    int bestScore = 0;
    for (const auto& repository : context.repositories) {
        if (!IsSelected(repository, context)) continue;
        const std::wstring name = Lower(repository.name);
        int score = loweredQuery.find(name) != std::wstring::npos ? 1000 + static_cast<int>(name.size()) : 0;
        size_t start = 0;
        while (start < name.size()) {
            while (start < name.size() && !std::iswalnum(name[start])) ++start;
            size_t end = start;
            while (end < name.size() && std::iswalnum(name[end])) ++end;
            const std::wstring token = name.substr(start, end - start);
            if (token.size() >= 4 && loweredQuery.find(token) != std::wstring::npos)
                score = std::max(score, static_cast<int>(token.size()));
            start = end + 1;
        }
        if (score > bestScore) { best = &repository; bestScore = score; }
    }
    return best;
}

Json ToolDefinition(const std::string& name, const std::string& description,
                    const Json& properties, const std::vector<std::string>& required) {
    Json parameters = Json::Object();
    parameters["type"] = Json("object");
    parameters["properties"] = properties;
    Json requiredValues = Json::Array();
    for (const auto& value : required) requiredValues.push(Json(value));
    parameters["required"] = requiredValues;
    Json function = Json::Object();
    function["name"] = Json(name);
    function["description"] = Json(description);
    function["parameters"] = parameters;
    Json tool = Json::Object();
    tool["type"] = Json("function");
    tool["function"] = function;
    return tool;
}

Json IntegerProperty(const char* description) {
    Json property = Json::Object();
    property["type"] = Json("integer");
    property["description"] = Json(description);
    return property;
}

Json StringProperty(const char* description) {
    Json property = Json::Object();
    property["type"] = Json("string");
    property["description"] = Json(description);
    return property;
}

} // namespace

Json AnalyticsToolDefinitions() {
    Json tools = Json::Array();
    Json year = Json::Object();
    year["year"] = IntegerProperty("Calendar year to analyze");
    Json date = Json::Object();
    date["date"] = StringProperty("Date in YYYY-MM-DD format");
    tools.push(ToolDefinition("get_day_summary", "Get commits for one day", date, {"date"}));
    tools.push(ToolDefinition("get_weekly_trend", "Get weekly commit totals", year, {"year"}));
    tools.push(ToolDefinition("get_repo_stats", "Get repository commit totals", year, {"year"}));
    tools.push(ToolDefinition("get_author_stats", "Get author commit totals", year, {"year"}));
    tools.push(ToolDefinition("get_hourly_dist", "Get hourly distribution for one day", date, {"date"}));
    tools.push(ToolDefinition("get_time_preferences",
        "Analyze submission time preferences across all selected repositories for a full year in one call. "
        "Use this tool for questions about preferred hours, earliest/latest submissions, or annual time patterns; "
        "do not call get_hourly_dist repeatedly.", year, {"year"}));
    Json repositoryOverview = year;
    repositoryOverview["query"] = StringProperty(
        "Repository name or the user's full repository-related question used to identify one selected repository");
    tools.push(ToolDefinition("get_repository_overview",
        "Analyze one repository's development and commit activity for a full year in one call. "
        "Returns totals, authors, monthly/weekday/hourly patterns, streak, peak days, and first/last commits. "
        "Use this instead of combining many global tools for a repository-specific question.",
        repositoryOverview, {"year", "query"}));
    tools.push(ToolDefinition("get_weekday_dist", "Get weekday distribution", year, {"year"}));
    tools.push(ToolDefinition("compare_years", "Compare a year with its previous year", year, {"year"}));
    tools.push(ToolDefinition("get_streak", "Get current and longest active-day streak", year, {"year"}));
    tools.push(ToolDefinition("get_monthly_trend", "Get monthly commit totals", year, {"year"}));
    Json peak = year;
    peak["n"] = IntegerProperty("Number of peak days, from 1 to 20");
    tools.push(ToolDefinition("get_peak_days", "Get the most active commit days", peak, {"year"}));
    return tools;
}

Json ExecuteAnalyticsTool(const std::string& name, const Json& arguments,
                          const AnalyticsContext& context, std::wstring& error) {
    error.clear();
    std::map<std::wstring, int> days;
    std::map<std::wstring, std::map<std::wstring, int>> byRepository;
    const int year = ArgYear(arguments, context);
    if (year < 1970 || year > 9999) return ErrorResult(L"年份参数无效");

    if (name == "get_time_preferences") {
        std::vector<DayEntry::CommitEntry> commits;
        if (!LoadSelectedCommits(context, commits, error)) return ErrorResult(error);
        const std::wstring prefix = YearPrefix(year);
        std::map<std::wstring, int> counts;
        const DayEntry::CommitEntry* earliest = nullptr;
        const DayEntry::CommitEntry* latest = nullptr;
        int total = 0;
        for (const auto& commit : commits) {
            if (commit.date.compare(0, prefix.size(), prefix) != 0) continue;
            const std::wstring hour = commit.time.size() >= 2 ? commit.time.substr(0, 2) : L"unknown";
            ++counts[hour];
            ++total;
            const std::wstring timeKey = commit.time + L" " + commit.date;
            if (!earliest || timeKey < earliest->time + L" " + earliest->date) earliest = &commit;
            if (!latest || timeKey > latest->time + L" " + latest->date) latest = &commit;
        }

        Json result = OkResult();
        result["year"] = Json(year);
        result["totalCommits"] = Json(total);
        Json hours = Json::Object();
        for (const auto& pair : counts) hours[WideToUtf8(pair.first)] = Json(pair.second);
        result["hours"] = hours;

        std::vector<std::pair<std::wstring, int>> ranked(counts.begin(), counts.end());
        std::sort(ranked.begin(), ranked.end(), [](const auto& left, const auto& right) {
            return left.second != right.second ? left.second > right.second : left.first < right.first;
        });
        Json topHours = Json::Array();
        for (size_t index = 0; index < std::min<size_t>(5, ranked.size()); ++index) {
            Json item = Json::Object();
            item["hour"] = Json(WideToUtf8(ranked[index].first));
            item["count"] = Json(ranked[index].second);
            topHours.push(item);
        }
        result["topHours"] = topHours;

        auto commitSnapshot = [](const DayEntry::CommitEntry* commit) {
            Json item = Json::Object();
            if (!commit) return item;
            item["date"] = Json(WideToUtf8(commit->date));
            item["time"] = Json(WideToUtf8(commit->time));
            item["repository"] = Json(WideToUtf8(commit->repoName));
            item["author"] = Json(WideToUtf8(commit->author));
            item["message"] = Json(WideToUtf8(commit->message));
            return item;
        };
        result["earliest"] = commitSnapshot(earliest);
        result["latest"] = commitSnapshot(latest);
        return result;
    }

    if (name == "get_repository_overview") {
        const Repository* repository = FindRepository(Utf8ToWide(arguments.get("query").string()), context);
        if (!repository) return ErrorResult(L"无法从问题中识别已选中的仓库");
        std::vector<DayEntry::CommitEntry> allCommits;
        if (!LoadSelectedCommits(context, allCommits, error)) return ErrorResult(error);
        const std::wstring prefix = YearPrefix(year);
        std::map<std::wstring, int> authors;
        std::map<std::wstring, int> hours;
        std::map<std::wstring, int> repositoryDays;
        std::array<int, 12> months = {};
        std::array<int, 7> weekdays = {};
        const DayEntry::CommitEntry* first = nullptr;
        const DayEntry::CommitEntry* last = nullptr;
        int total = 0;
        for (const auto& commit : allCommits) {
            if (commit.repoName != repository->name || commit.date.compare(0, prefix.size(), prefix) != 0) continue;
            ++total;
            ++repositoryDays[commit.date];
            ++authors[commit.author.empty() ? L"unknown" : commit.author];
            ++hours[commit.time.size() >= 2 ? commit.time.substr(0, 2) : L"unknown"];
            if (commit.date.size() >= 7) {
                const int month = std::stoi(commit.date.substr(5, 2)) - 1;
                if (month >= 0 && month < 12) ++months[static_cast<size_t>(month)];
            }
            const int weekday = Weekday(commit.date);
            if (weekday >= 0 && weekday < 7) ++weekdays[static_cast<size_t>(weekday)];
            const std::wstring timestamp = commit.date + L" " + commit.time;
            if (!first || timestamp < first->date + L" " + first->time) first = &commit;
            if (!last || timestamp > last->date + L" " + last->time) last = &commit;
        }

        Json result = OkResult();
        result["repository"] = Json(WideToUtf8(repository->name));
        result["year"] = Json(year);
        result["totalCommits"] = Json(total);
        result["activeDays"] = Json(static_cast<int>(repositoryDays.size()));

        auto rankedObject = [](const std::map<std::wstring, int>& values, const char* label) {
            std::vector<std::pair<std::wstring, int>> ranked(values.begin(), values.end());
            std::sort(ranked.begin(), ranked.end(), [](const auto& left, const auto& right) {
                return left.second != right.second ? left.second > right.second : left.first < right.first;
            });
            Json output = Json::Array();
            for (size_t index = 0; index < std::min<size_t>(5, ranked.size()); ++index) {
                Json item = Json::Object();
                item[label] = Json(WideToUtf8(ranked[index].first));
                item["count"] = Json(ranked[index].second);
                output.push(item);
            }
            return output;
        };
        result["topAuthors"] = rankedObject(authors, "author");
        result["topHours"] = rankedObject(hours, "hour");

        Json monthValues = Json::Array();
        for (int count : months) monthValues.push(Json(count));
        result["months"] = monthValues;
        Json weekdayValues = Json::Array();
        for (int count : weekdays) weekdayValues.push(Json(count));
        result["weekdays"] = weekdayValues;

        std::vector<std::pair<std::wstring, int>> peakDays(repositoryDays.begin(), repositoryDays.end());
        std::sort(peakDays.begin(), peakDays.end(), [](const auto& left, const auto& right) {
            return left.second != right.second ? left.second > right.second : left.first < right.first;
        });
        Json peaks = Json::Array();
        for (size_t index = 0; index < std::min<size_t>(5, peakDays.size()); ++index) {
            Json item = Json::Object();
            item["date"] = Json(WideToUtf8(peakDays[index].first));
            item["count"] = Json(peakDays[index].second);
            peaks.push(item);
        }
        result["peakDays"] = peaks;

        std::set<int> activeDayNumbers;
        for (const auto& pair : repositoryDays) activeDayNumbers.insert(DayOfYear(pair.first));
        int longest = 0;
        int running = 0;
        for (int day = 0; day < 366; ++day) {
            running = activeDayNumbers.count(day) ? running + 1 : 0;
            longest = std::max(longest, running);
        }
        result["longestStreak"] = Json(longest);

        auto commitSnapshot = [](const DayEntry::CommitEntry* commit) {
            Json item = Json::Object();
            if (!commit) return item;
            item["date"] = Json(WideToUtf8(commit->date));
            item["time"] = Json(WideToUtf8(commit->time));
            item["author"] = Json(WideToUtf8(commit->author));
            item["message"] = Json(WideToUtf8(commit->message));
            return item;
        };
        result["firstCommit"] = commitSnapshot(first);
        result["lastCommit"] = commitSnapshot(last);
        return result;
    }

    if (name == "get_day_summary" || name == "get_hourly_dist" || name == "get_author_stats") {
        std::vector<DayEntry::CommitEntry> commits;
        if (!LoadSelectedCommits(context, commits, error)) return ErrorResult(error);
        const std::wstring date = Utf8ToWide(arguments.get("date").string());
        const std::wstring prefix = name == "get_author_stats" ? YearPrefix(year) : date;
        if (name == "get_day_summary") {
            Json result = OkResult();
            Json values = Json::Array();
            int total = 0;
            for (const auto& commit : commits) {
                if (commit.date != date) continue;
                Json item = Json::Object();
                item["time"] = Json(WideToUtf8(commit.time));
                item["repository"] = Json(WideToUtf8(commit.repoName));
                item["message"] = Json(WideToUtf8(commit.message));
                item["author"] = Json(WideToUtf8(commit.author));
                values.push(item);
                if (++total >= 200) break;
            }
            result["date"] = Json(WideToUtf8(date));
            result["total"] = Json(total);
            result["commits"] = values;
            return result;
        }
        std::map<std::wstring, int> counts;
        for (const auto& commit : commits) {
            if (commit.date.compare(0, prefix.size(), prefix) != 0) continue;
            if (name == "get_hourly_dist") counts[commit.time.size() >= 2 ? commit.time.substr(0, 2) : L"unknown"]++;
            else counts[commit.author.empty() ? L"unknown" : commit.author]++;
        }
        Json result = OkResult();
        Json values = Json::Object();
        for (const auto& pair : counts) values[WideToUtf8(pair.first)] = Json(pair.second);
        result[name == "get_hourly_dist" ? "hours" : "authors"] = values;
        return result;
    }

    if (!LoadSelectedDays(context, days, byRepository, error)) return ErrorResult(error);
    const std::wstring prefix = YearPrefix(year);
    Json result = OkResult();

    if (name == "get_repo_stats") {
        Json values = Json::Object();
        for (const auto& repository : byRepository) {
            int total = 0;
            for (const auto& pair : repository.second) if (pair.first.compare(0, prefix.size(), prefix) == 0) total += pair.second;
            values[WideToUtf8(repository.first)] = Json(total);
        }
        result["repositories"] = values;
        return result;
    }

    if (name == "compare_years") {
        int current = 0, previous = 0;
        const std::wstring previousPrefix = YearPrefix(year - 1);
        for (const auto& pair : days) {
            if (pair.first.compare(0, prefix.size(), prefix) == 0) current += pair.second;
            else if (pair.first.compare(0, previousPrefix.size(), previousPrefix) == 0) previous += pair.second;
        }
        result["year"] = Json(year);
        result["current"] = Json(current);
        result["previous"] = Json(previous);
        result["change"] = Json(current - previous);
        result["changePercent"] = Json(previous ? (current - previous) * 100.0 / previous : 0.0);
        return result;
    }

    if (name == "get_monthly_trend" || name == "get_weekly_trend" || name == "get_weekday_dist") {
        const int size = name == "get_monthly_trend" ? 12 : name == "get_weekly_trend" ? 54 : 7;
        std::vector<int> counts(static_cast<size_t>(size), 0);
        for (const auto& pair : days) {
            if (pair.first.compare(0, prefix.size(), prefix) != 0) continue;
            int index = 0;
            if (name == "get_monthly_trend" && pair.first.size() >= 7) index = std::stoi(pair.first.substr(5, 2)) - 1;
            else if (name == "get_weekly_trend") index = DayOfYear(pair.first) / 7;
            else index = Weekday(pair.first);
            if (index >= 0 && index < size) counts[static_cast<size_t>(index)] += pair.second;
        }
        Json values = Json::Array();
        for (int count : counts) values.push(Json(count));
        result[name == "get_monthly_trend" ? "months" : name == "get_weekly_trend" ? "weeks" : "weekdays"] = values;
        return result;
    }

    std::vector<std::pair<std::wstring, int>> active;
    for (const auto& pair : days) if (pair.first.compare(0, prefix.size(), prefix) == 0 && pair.second > 0) active.push_back(pair);
    if (name == "get_peak_days") {
        std::sort(active.begin(), active.end(), [](const auto& left, const auto& right) {
            return left.second != right.second ? left.second > right.second : left.first < right.first;
        });
        const int limit = std::max(1, std::min(20, arguments.get("n").integer(5)));
        Json values = Json::Array();
        for (int i = 0; i < limit && i < static_cast<int>(active.size()); ++i) {
            Json item = Json::Object();
            item["date"] = Json(WideToUtf8(active[static_cast<size_t>(i)].first));
            item["count"] = Json(active[static_cast<size_t>(i)].second);
            values.push(item);
        }
        result["days"] = values;
        return result;
    }
    if (name == "get_streak") {
        std::set<int> activeDays;
        for (const auto& pair : active) activeDays.insert(DayOfYear(pair.first));
        int longest = 0, running = 0;
        for (int day = 0; day < 366; ++day) {
            running = activeDays.count(day) ? running + 1 : 0;
            longest = std::max(longest, running);
        }
        int current = 0;
        if (!activeDays.empty()) {
            int day = *activeDays.rbegin();
            while (activeDays.count(day--)) ++current;
        }
        result["current"] = Json(current);
        result["longest"] = Json(longest);
        return result;
    }
    return ErrorResult(L"未知工具");
}
