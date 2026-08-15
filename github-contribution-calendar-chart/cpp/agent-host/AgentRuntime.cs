using System.Collections.Concurrent;
using System.Text;
using System.Text.Json.Nodes;

namespace GitLocal.Agent;

internal sealed class AgentRuntime
{
    private const string SystemPrompt = """
        你是Git Local中的本地Git分析助手。你必须基于工具返回的数据回答，不得编造提交、仓库或作者信息。
        当问题涉及具体统计时主动调用工具。回答使用中文，先给结论，再给关键证据；工具失败时明确说明。
        涉及全年提交时间偏好、最常用提交时段、最早或最晚提交时，优先调用get_time_preferences一次完成全年聚合，不要逐日重复调用get_hourly_dist。
        涉及一个具体仓库的开发或提交状况时，优先调用get_repository_overview一次完成聚合，不要组合多个全局统计工具。
        不输出绝对文件路径，不声称修改了仓库。用户要求记住的信息可以跨会话使用。
        """;

    private readonly RpcPeer _rpc;
    private readonly SessionStore _store;
    private readonly AgnesProvider _provider = new();
    private readonly ConcurrentDictionary<string, CancellationTokenSource> _turns = new();
    private ProviderConfig? _config;
    private JsonArray _tools = new();

    public AgentRuntime(RpcPeer rpc, SessionStore store)
    {
        _rpc = rpc;
        _store = store;
    }

    public void Initialize(ProviderConfig config, JsonArray tools)
    {
        _config = config;
        _tools = tools;
    }

    public void StartTurn(string sessionId, string turnId, string message, JsonObject context)
    {
        if (_config is null) throw new InvalidOperationException("Agent尚未初始化");
        if (string.IsNullOrWhiteSpace(message)) throw new ArgumentException("消息不能为空");
        var cancellation = new CancellationTokenSource(TimeSpan.FromMinutes(3));
        if (!_turns.TryAdd(turnId, cancellation)) throw new InvalidOperationException("turnId重复");
        _ = Task.Run(() => RunTurnAsync(sessionId, turnId, message.Trim(), context, cancellation.Token));
    }

    public bool Cancel(string turnId)
    {
        if (!_turns.TryGetValue(turnId, out var cancellation)) return false;
        cancellation.Cancel();
        return true;
    }

    private async Task RunTurnAsync(string sessionId, string turnId, string message,
                                    JsonObject context, CancellationToken cancellationToken)
    {
        try
        {
            await _rpc.NotifyAsync("chat.started", Event(sessionId, turnId), cancellationToken);
            await _store.EnsureSessionAsync(sessionId, message);
            await _store.AddMessageAsync(sessionId, new StoredMessage("user", message, null, null, null));

            var scope = context["projectScope"]?.GetValue<string>() ?? "global";
            if (message.Contains("记住", StringComparison.Ordinal)) await _store.AddMemoryAsync(scope, message);
            var memories = await _store.FindMemoriesAsync(scope, message);
            var checkpoint = await _store.GetCheckpointAsync(sessionId);
            var stored = await _store.GetMessagesAsync(sessionId);
            var activeStored = checkpoint is null
                ? stored
                : stored.Where(item => item.Id > checkpoint.ThroughMessageId).ToList();
            var messages = BuildMessages(activeStored, memories, context, checkpoint?.Summary);
            messages = await CompactIfNeededAsync(sessionId, messages, activeStored, checkpoint, cancellationToken);

            if (IsTimePreferenceQuery(message))
            {
                var toolCallId = "direct-time-" + Guid.NewGuid().ToString("N");
                var analysisYear = context["activeYear"]?.GetValue<int>() ?? DateTime.Now.Year;
                var arguments = new JsonObject
                {
                    ["year"] = analysisYear
                };
                var callsJson = new JsonArray(new JsonObject
                {
                    ["id"] = toolCallId, ["type"] = "function",
                    ["function"] = new JsonObject
                    {
                        ["name"] = "get_time_preferences", ["arguments"] = arguments.ToJsonString()
                    }
                });
                messages.Insert(messages.Count, new JsonObject
                {
                    ["role"] = "assistant", ["content"] = string.Empty, ["tool_calls"] = callsJson.DeepClone()
                });
                await _store.AddMessageAsync(sessionId,
                    new StoredMessage("assistant", string.Empty, null, null, callsJson));
                await _rpc.NotifyAsync("chat.tool_started", Event(sessionId, turnId,
                    new JsonObject { ["toolCallId"] = toolCallId, ["name"] = "get_time_preferences" }), cancellationToken);
                var toolResult = await _rpc.CallAsync("tool.execute", new JsonObject
                {
                    ["sessionId"] = sessionId, ["turnId"] = turnId, ["toolCallId"] = toolCallId,
                    ["name"] = "get_time_preferences", ["arguments"] = arguments.DeepClone(),
                    ["context"] = context.DeepClone()
                }, cancellationToken);
                var toolContent = toolResult?.ToJsonString() ?? "{\"ok\":false,\"error\":\"工具无返回\"}";
                messages.Insert(messages.Count, new JsonObject
                {
                    ["role"] = "tool", ["tool_call_id"] = toolCallId,
                    ["name"] = "get_time_preferences", ["content"] = toolContent
                });
                await _store.AddMessageAsync(sessionId,
                    new StoredMessage("tool", toolContent, toolCallId, "get_time_preferences", null));
                await _rpc.NotifyAsync("chat.tool_completed", Event(sessionId, turnId,
                    new JsonObject { ["toolCallId"] = toolCallId, ["name"] = "get_time_preferences" }), cancellationToken);

                var final = FormatTimePreferenceAnswer(toolResult, analysisYear);
                await PublishCompletedAsync(sessionId, turnId, final, cancellationToken, emitDelta: true);
                return;
            }

            if (IsRepositoryOverviewQuery(message, context))
            {
                var analysisYear = context["activeYear"]?.GetValue<int>() ?? DateTime.Now.Year;
                var toolCallId = "direct-repository-" + Guid.NewGuid().ToString("N");
                var arguments = new JsonObject { ["year"] = analysisYear, ["query"] = message };
                var callsJson = new JsonArray(new JsonObject
                {
                    ["id"] = toolCallId, ["type"] = "function",
                    ["function"] = new JsonObject
                    {
                        ["name"] = "get_repository_overview", ["arguments"] = arguments.ToJsonString()
                    }
                });
                messages.Insert(messages.Count, new JsonObject
                {
                    ["role"] = "assistant", ["content"] = string.Empty, ["tool_calls"] = callsJson.DeepClone()
                });
                await _store.AddMessageAsync(sessionId,
                    new StoredMessage("assistant", string.Empty, null, null, callsJson));
                await _rpc.NotifyAsync("chat.tool_started", Event(sessionId, turnId,
                    new JsonObject { ["toolCallId"] = toolCallId, ["name"] = "get_repository_overview" }), cancellationToken);
                var toolResult = await _rpc.CallAsync("tool.execute", new JsonObject
                {
                    ["sessionId"] = sessionId, ["turnId"] = turnId, ["toolCallId"] = toolCallId,
                    ["name"] = "get_repository_overview", ["arguments"] = arguments.DeepClone(),
                    ["context"] = context.DeepClone()
                }, cancellationToken);
                var toolContent = toolResult?.ToJsonString() ?? "{\"ok\":false,\"error\":\"工具无返回\"}";
                messages.Insert(messages.Count, new JsonObject
                {
                    ["role"] = "tool", ["tool_call_id"] = toolCallId,
                    ["name"] = "get_repository_overview", ["content"] = toolContent
                });
                await _store.AddMessageAsync(sessionId,
                    new StoredMessage("tool", toolContent, toolCallId, "get_repository_overview", null));
                await _rpc.NotifyAsync("chat.tool_completed", Event(sessionId, turnId,
                    new JsonObject { ["toolCallId"] = toolCallId, ["name"] = "get_repository_overview" }), cancellationToken);
                var final = FormatRepositoryOverviewAnswer(toolResult, analysisYear);
                await PublishCompletedAsync(sessionId, turnId, final, cancellationToken, emitDelta: true);
                return;
            }

            var seenToolCalls = new HashSet<string>(StringComparer.Ordinal);
            for (var round = 0; round < 8; round++)
            {
                var result = await _provider.CompleteAsync(_config!, messages, _tools, async delta =>
                {
                    await _rpc.NotifyAsync("chat.delta", Event(sessionId, turnId, new JsonObject { ["text"] = delta }), cancellationToken);
                }, cancellationToken);

                if (result.ToolCalls.Count == 0)
                {
                    var usedFallback = string.IsNullOrWhiteSpace(result.Content);
                    var content = usedFallback ? FallbackSummary(messages, "模型没有生成文字回答") : result.Content;
                    await PublishCompletedAsync(sessionId, turnId, content, cancellationToken, usedFallback);
                    return;
                }

                var callsJson = new JsonArray();
                foreach (var call in result.ToolCalls) callsJson.Insert(callsJson.Count, new JsonObject
                {
                    ["id"] = call.Id, ["type"] = "function",
                    ["function"] = new JsonObject { ["name"] = call.Name, ["arguments"] = call.Arguments.ToJsonString() }
                });
                messages.Insert(messages.Count, new JsonObject { ["role"] = "assistant", ["content"] = result.Content, ["tool_calls"] = callsJson.DeepClone() });
                await _store.AddMessageAsync(sessionId, new StoredMessage("assistant", result.Content, null, null, callsJson));

                var repeatedCall = false;
                foreach (var call in result.ToolCalls)
                {
                    var signature = call.Name + "|" + call.Arguments.ToJsonString();
                    var duplicateCall = !seenToolCalls.Add(signature);
                    if (duplicateCall) repeatedCall = true;
                    await _rpc.NotifyAsync("chat.tool_started", Event(sessionId, turnId,
                        new JsonObject { ["toolCallId"] = call.Id, ["name"] = call.Name }), cancellationToken);
                    JsonNode? toolResult;
                    if (!duplicateCall)
                    {
                        toolResult = await _rpc.CallAsync("tool.execute", new JsonObject
                        {
                            ["sessionId"] = sessionId, ["turnId"] = turnId, ["toolCallId"] = call.Id,
                            ["name"] = call.Name, ["arguments"] = call.Arguments.DeepClone(), ["context"] = context.DeepClone()
                        }, cancellationToken);
                    }
                    else
                    {
                        repeatedCall = true;
                        toolResult = new JsonObject
                        {
                            ["ok"] = false,
                            ["error"] = "检测到重复工具调用，已有结果将用于总结。"
                        };
                    }
                    var content = toolResult?.ToJsonString() ?? "{\"ok\":false,\"error\":\"工具无返回\"}";
                    messages.Insert(messages.Count, new JsonObject
                    {
                        ["role"] = "tool", ["tool_call_id"] = call.Id,
                        ["name"] = call.Name, ["content"] = content
                    });
                    await _store.AddMessageAsync(sessionId, new StoredMessage("tool", content, call.Id, call.Name, null));
                    await _rpc.NotifyAsync("chat.tool_completed", Event(sessionId, turnId,
                        new JsonObject { ["toolCallId"] = call.Id, ["name"] = call.Name }), cancellationToken);
                }
                if (repeatedCall)
                {
                    var final = await SynthesizeAsync(sessionId, turnId, messages,
                        "检测到模型重复调用同一工具。请基于已有工具结果直接给出最终中文结论，不要再调用工具。",
                        cancellationToken);
                    var usedFallback = string.IsNullOrWhiteSpace(final);
                    await PublishCompletedAsync(sessionId, turnId,
                        usedFallback ? FallbackSummary(messages, "工具调用已完成") : final,
                        cancellationToken, usedFallback);
                    return;
                }
            }
            var summary = await SynthesizeAsync(sessionId, turnId, messages,
                "工具分析轮次已达上限。请基于已有工具结果直接给出最终中文结论，不要再调用工具。",
                cancellationToken);
            var summaryFallback = string.IsNullOrWhiteSpace(summary);
            await PublishCompletedAsync(sessionId, turnId,
                summaryFallback ? FallbackSummary(messages, "工具调用轮次已达上限") : summary,
                cancellationToken, summaryFallback);
        }
        catch (OperationCanceledException)
        {
            await _rpc.NotifyAsync("chat.failed", Event(sessionId, turnId,
                new JsonObject { ["code"] = "cancelled", ["message"] = "请求已取消" }));
        }
        catch (Exception ex)
        {
            await _rpc.NotifyAsync("chat.failed", Event(sessionId, turnId,
                new JsonObject { ["code"] = Classify(ex), ["message"] = ex.Message }));
        }
        finally
        {
            if (_turns.TryRemove(turnId, out var cancellation)) cancellation.Dispose();
        }
    }

    private async Task<string> SynthesizeAsync(string sessionId, string turnId, JsonArray messages, string instruction,
                                                CancellationToken cancellationToken)
    {
        messages.Insert(messages.Count, new JsonObject { ["role"] = "system", ["content"] = instruction });
        try
        {
            var result = await _provider.CompleteAsync(_config!, messages, new JsonArray(), async delta =>
            {
                await _rpc.NotifyAsync("chat.delta", Event(sessionId, turnId,
                    new JsonObject { ["text"] = delta }), cancellationToken);
            }, cancellationToken);
            return result.ToolCalls.Count == 0 ? result.Content : string.Empty;
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            return string.Empty;
        }
    }

    private async Task PublishCompletedAsync(string sessionId, string turnId, string content,
                                              CancellationToken cancellationToken, bool emitDelta = false)
    {
        if (emitDelta)
            await _rpc.NotifyAsync("chat.delta", Event(sessionId, turnId,
                new JsonObject { ["text"] = content }), cancellationToken);
        await _store.AddMessageAsync(sessionId, new StoredMessage("assistant", content, null, null, null));
        await _rpc.NotifyAsync("chat.completed", Event(sessionId, turnId,
            new JsonObject { ["text"] = content }), cancellationToken);
    }

    private static string FallbackSummary(JsonArray messages, string reason)
    {
        var toolResults = messages
            .Where(node => node?["role"]?.GetValue<string>() == "tool")
            .Select(node => node?["content"]?.GetValue<string>())
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Select(value => value!)
            .ToList();
        var details = toolResults.LastOrDefault(IsUsableToolResult)
            ?? (toolResults.Count == 0 ? "无工具返回数据。" : toolResults[^1]!);
        if (details.Length > 4000) details = details[..4000] + "...";
        return reason + "。\n工具返回数据：\n" + details;
    }

    private static bool IsUsableToolResult(string value)
    {
        try
        {
            var error = JsonNode.Parse(value)?["error"]?.GetValue<string>();
            return error is null || !error.Contains("重复工具调用", StringComparison.Ordinal);
        }
        catch { return true; }
    }

    private static bool IsTimePreferenceQuery(string message)
    {
        var value = message.ToLowerInvariant();
        var mentionsCommits = value.Contains("提交", StringComparison.Ordinal) ||
                              value.Contains("commit", StringComparison.Ordinal);
        var mentionsTime = value.Contains("时间", StringComparison.Ordinal) ||
                           value.Contains("几点", StringComparison.Ordinal) ||
                           value.Contains("时段", StringComparison.Ordinal) ||
                           value.Contains("小时", StringComparison.Ordinal) ||
                           value.Contains("最早", StringComparison.Ordinal) ||
                           value.Contains("最晚", StringComparison.Ordinal) ||
                           value.Contains("偏好", StringComparison.Ordinal) ||
                           value.Contains("hour", StringComparison.Ordinal) ||
                           value.Contains("time", StringComparison.Ordinal) ||
                           value.Contains("earliest", StringComparison.Ordinal) ||
                           value.Contains("latest", StringComparison.Ordinal);
        return mentionsCommits && mentionsTime;
    }

    private static bool IsRepositoryOverviewQuery(string message, JsonObject context)
    {
        var value = message.ToLowerInvariant();
        var asksForOverview = value.Contains("提交", StringComparison.Ordinal) ||
                              value.Contains("开发", StringComparison.Ordinal) ||
                              value.Contains("状况", StringComparison.Ordinal) ||
                              value.Contains("情况", StringComparison.Ordinal) ||
                              value.Contains("表现", StringComparison.Ordinal) ||
                              value.Contains("活跃", StringComparison.Ordinal) ||
                              value.Contains("分析", StringComparison.Ordinal) ||
                              value.Contains("overview", StringComparison.Ordinal) ||
                              value.Contains("activity", StringComparison.Ordinal);
        if (!asksForOverview || context["selectedRepositories"] is not JsonArray repositories) return false;

        foreach (var item in repositories)
        {
            var repository = item?.GetValue<string>();
            if (string.IsNullOrWhiteSpace(repository)) continue;
            if (value.Contains(repository.ToLowerInvariant(), StringComparison.Ordinal)) return true;
            var tokens = repository.Split(new[] { '_', '-', ' ', '.', '/', '\\' },
                                          StringSplitOptions.RemoveEmptyEntries);
            if (tokens.Any(token => token.Length >= 4 &&
                                    value.Contains(token.ToLowerInvariant(), StringComparison.Ordinal)))
                return true;
        }
        return false;
    }

    private static string FormatTimePreferenceAnswer(JsonNode? toolResult, int year)
    {
        if (toolResult is not JsonObject result)
            return $"无法完成{year}年提交时间分析：本地工具没有返回有效数据。";
        if (result["ok"]?.GetValue<bool>() != true)
            return $"无法完成{year}年提交时间分析：{result["error"]?.GetValue<string>() ?? "未知工具错误"}";

        var total = result["totalCommits"]?.GetValue<int>() ?? 0;
        if (total == 0) return $"{year}年没有找到可分析的提交记录。";

        var text = new StringBuilder();
        text.AppendLine($"{year}年共分析 {total} 次提交。基于一天中的提交钟点统计：");
        if (result["topHours"] is JsonArray topHours && topHours.Count > 0)
        {
            var top = topHours[0]?.AsObject();
            var topHour = top?["hour"]?.GetValue<string>() ?? "unknown";
            var topCount = top?["count"]?.GetValue<int>() ?? 0;
            text.AppendLine($"- 最常提交时段：{topHour}:00-{topHour}:59，共 {topCount} 次，占 {(topCount * 100.0 / total):F1}%。");
            text.Append("- 高频时段：");
            var values = new List<string>();
            foreach (var node in topHours)
            {
                var item = node?.AsObject();
                var hour = item?["hour"]?.GetValue<string>() ?? "unknown";
                var count = item?["count"]?.GetValue<int>() ?? 0;
                values.Add($"{hour}时 {count}次");
            }
            text.AppendLine(string.Join("，", values) + "。");
        }
        text.AppendLine("- 全年最早钟点提交：" + FormatCommitSnapshot(result["earliest"]));
        text.Append("- 全年最晚钟点提交：" + FormatCommitSnapshot(result["latest"]));
        return text.ToString();
    }

    private static string FormatCommitSnapshot(JsonNode? node)
    {
        if (node is not JsonObject item || item.Count == 0) return "无数据";
        var date = item["date"]?.GetValue<string>() ?? "未知日期";
        var time = item["time"]?.GetValue<string>() ?? "未知时间";
        var repository = item["repository"]?.GetValue<string>();
        var message = item["message"]?.GetValue<string>();
        var details = new List<string>();
        if (!string.IsNullOrWhiteSpace(repository)) details.Add("仓库：" + repository);
        if (!string.IsNullOrWhiteSpace(message)) details.Add("提交：" + message);
        return details.Count == 0 ? $"{date} {time}" : $"{date} {time}（{string.Join("；", details)}）";
    }

    private static string FormatRepositoryOverviewAnswer(JsonNode? toolResult, int year)
    {
        if (toolResult is not JsonObject result)
            return $"无法完成{year}年仓库提交分析：本地工具没有返回有效数据。";
        if (result["ok"]?.GetValue<bool>() != true)
            return $"无法完成{year}年仓库提交分析：{result["error"]?.GetValue<string>() ?? "未知工具错误"}";

        var repository = result["repository"]?.GetValue<string>() ?? "目标仓库";
        var total = result["totalCommits"]?.GetValue<int>() ?? 0;
        var activeDays = result["activeDays"]?.GetValue<int>() ?? 0;
        if (total == 0) return $"{repository} 在 {year} 年没有找到可分析的提交记录。";

        var text = new StringBuilder();
        var averagePerActiveDay = total * 1.0 / Math.Max(1, activeDays);
        var activityAssessment = activeDays >= 120 && averagePerActiveDay >= 2.0
            ? "整体活跃度较高，提交节奏比较稳定。"
            : activeDays >= 40
                ? "整体活跃度中等，提交主要集中在部分阶段。"
                : "整体活跃度偏低，提交较为零散。";
        text.AppendLine($"{repository} 在 {year} 年共有 {total} 次提交，分布在 {activeDays} 个活跃日，平均每个活跃日 {averagePerActiveDay:F1} 次。");
        text.AppendLine("总体评价：" + activityAssessment);
        text.AppendLine($"- 最长连续提交：{result["longestStreak"]?.GetValue<int>() ?? 0} 天。");

        if (result["topAuthors"] is JsonArray authors && authors.Count > 0)
        {
            var author = authors[0]?.AsObject();
            text.AppendLine($"- 主要提交者：{author?["author"]?.GetValue<string>() ?? "unknown"}，{author?["count"]?.GetValue<int>() ?? 0} 次。");
        }
        if (result["months"] is JsonArray months && months.Count > 0)
        {
            var peakMonth = 0;
            var peakCount = -1;
            for (var index = 0; index < months.Count; index++)
            {
                var count = months[index]?.GetValue<int>() ?? 0;
                if (count > peakCount) { peakMonth = index + 1; peakCount = count; }
            }
            text.AppendLine($"- 最活跃月份：{peakMonth} 月，{peakCount} 次提交。");
        }
        if (result["weekdays"] is JsonArray weekdays && weekdays.Count == 7)
        {
            var labels = new[] { "周日", "周一", "周二", "周三", "周四", "周五", "周六" };
            var peakDay = 0;
            var peakCount = -1;
            for (var index = 0; index < weekdays.Count; index++)
            {
                var count = weekdays[index]?.GetValue<int>() ?? 0;
                if (count > peakCount) { peakDay = index; peakCount = count; }
            }
            text.AppendLine($"- 最常提交星期：{labels[peakDay]}，{peakCount} 次。");
        }
        if (result["topHours"] is JsonArray hours && hours.Count > 0)
        {
            var hour = hours[0]?.AsObject();
            text.AppendLine($"- 最常提交时段：{hour?["hour"]?.GetValue<string>() ?? "unknown"}:00，{hour?["count"]?.GetValue<int>() ?? 0} 次。");
        }
        if (result["peakDays"] is JsonArray peaks && peaks.Count > 0)
        {
            var values = peaks.Select(node =>
            {
                var item = node?.AsObject();
                return $"{item?["date"]?.GetValue<string>() ?? "未知日期"}（{item?["count"]?.GetValue<int>() ?? 0}次）";
            });
            text.AppendLine("- 提交峰值日：" + string.Join("，", values) + "。");
        }
        text.AppendLine("- 年内首次提交：" + FormatCommitSnapshot(result["firstCommit"]));
        text.Append("- 年内最后提交：" + FormatCommitSnapshot(result["lastCommit"]));
        return text.ToString();
    }

    private async Task<JsonArray> CompactIfNeededAsync(string sessionId, JsonArray messages,
                                                        List<StoredMessage> activeStored,
                                                        StoredCheckpoint? checkpoint,
                                                        CancellationToken cancellationToken)
    {
        var estimate = Encoding.UTF8.GetByteCount(messages.ToJsonString()) / 3 + messages.Count * 8;
        if (estimate < _config!.ContextWindowTokens * 0.7 || activeStored.Count <= 16) return messages;
        var oldCount = activeStored.Count - 16;
        var storedStart = messages.Count - activeStored.Count;
        var old = new JsonArray();
        if (checkpoint is not null) old.Insert(old.Count, new JsonObject { ["role"] = "system", ["content"] = checkpoint.Summary });
        for (var i = 0; i < oldCount; i++) old.Insert(old.Count, messages[storedStart + i]?.DeepClone());
        string summary;
        try { summary = await _provider.SummarizeAsync(_config, old, cancellationToken); }
        catch { summary = "历史对话已压缩；如需早期细节，请查看已保存会话。"; }
        await _store.SaveCheckpointAsync(sessionId, summary, activeStored[oldCount - 1].Id);
        var compacted = new JsonArray();
        compacted.Insert(compacted.Count, messages[0]?.DeepClone());
        compacted.Insert(compacted.Count, new JsonObject { ["role"] = "system", ["content"] = "历史摘要：" + summary });
        for (var i = storedStart + oldCount; i < messages.Count; i++) compacted.Insert(compacted.Count, messages[i]?.DeepClone());
        return compacted;
    }

    private static JsonArray BuildMessages(List<StoredMessage> stored, List<string> memories,
                                           JsonObject context, string? checkpointSummary)
    {
        var messages = new JsonArray();
        var contextText = context.ToJsonString();
        var memoryText = memories.Count == 0 ? "无" : string.Join("\n", memories.Select(x => "- " + x));
        messages.Insert(messages.Count, new JsonObject
        {
            ["role"] = "system", ["content"] = SystemPrompt + "\n当前应用上下文：" + contextText + "\n显式长期记忆：\n" + memoryText
        });
        if (!string.IsNullOrWhiteSpace(checkpointSummary))
            messages.Insert(messages.Count, new JsonObject { ["role"] = "system", ["content"] = "历史摘要：" + checkpointSummary });
        foreach (var message in stored)
        {
            var node = new JsonObject { ["role"] = message.Role, ["content"] = message.Content };
            if (message.ToolCallId is not null) node["tool_call_id"] = message.ToolCallId;
            if (message.Name is not null) node["name"] = message.Name;
            if (message.ToolCalls is not null) node["tool_calls"] = message.ToolCalls.DeepClone();
            messages.Insert(messages.Count, node);
        }
        return messages;
    }

    private static JsonObject Event(string sessionId, string turnId, JsonObject? extra = null)
    {
        var result = extra ?? new JsonObject();
        result["sessionId"] = sessionId;
        result["turnId"] = turnId;
        return result;
    }

    private static string Classify(Exception ex) => ex switch
    {
        ProviderException provider when provider.StatusCode == 401 => "unauthorized",
        ProviderException provider when provider.StatusCode == 429 => "rate_limited",
        ProviderException => "provider_error",
        TimeoutException => "timeout",
        _ => "internal_error"
    };
}
