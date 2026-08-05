#pragma once

#include "json.h"
#include "types.h"

#include <string>
#include <unordered_map>
#include <vector>

struct AnalyticsContext {
    int activeYear = 0;
    std::wstring selectedDate;
    std::vector<Repository> repositories;
    std::unordered_map<std::wstring, bool> selected;
};

Json ExecuteAnalyticsTool(const std::string& name, const Json& arguments,
                          const AnalyticsContext& context, std::wstring& error);
Json AnalyticsToolDefinitions();
