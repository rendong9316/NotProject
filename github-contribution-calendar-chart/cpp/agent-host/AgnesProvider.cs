using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json.Nodes;

namespace GitLocal.Agent;

internal sealed record ProviderConfig(string BaseUrl, string Model, string ApiKey,
                                      int MaxTokens, int ContextWindowTokens, double Temperature);
internal sealed record ToolCall(string Id, string Name, JsonObject Arguments);
internal sealed record ProviderResult(string Content, IReadOnlyList<ToolCall> ToolCalls);

internal sealed class ProviderException : Exception
{
    public int StatusCode { get; }
    public ProviderException(int statusCode, string message) : base(message) => StatusCode = statusCode;
}

internal sealed class AgnesProvider
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(90) };

    public async Task<ProviderResult> CompleteAsync(ProviderConfig config, JsonArray messages,
        JsonArray tools, Func<string, Task> onDelta, CancellationToken cancellationToken)
    {
        for (var attempt = 0; ; attempt++)
        {
            try { return await CompleteOnceAsync(config, messages, tools, onDelta, cancellationToken); }
            catch (ProviderException ex) when ((ex.StatusCode == 429 || ex.StatusCode >= 500) && attempt < 2)
            {
                await Task.Delay(TimeSpan.FromMilliseconds(attempt == 0 ? 500 : 1500), cancellationToken);
            }
        }
    }

    public async Task<string> SummarizeAsync(ProviderConfig config, JsonArray messages,
                                              CancellationToken cancellationToken)
    {
        var prompt = new JsonArray();
        prompt.Insert(prompt.Count, new JsonObject
        {
            ["role"] = "system",
            ["content"] = "将对话压缩成简洁中文JSON，字段为goal、facts、decisions、tool_results、open_items。只保留已确认信息。"
        });
        prompt.Insert(prompt.Count, new JsonObject { ["role"] = "user", ["content"] = messages.ToJsonString() });
        var requestBody = new JsonObject
        {
            ["model"] = config.Model, ["messages"] = prompt, ["temperature"] = 0,
            ["max_tokens"] = 1200, ["stream"] = false
        };
        using var request = CreateRequest(config, requestBody);
        using var response = await _http.SendAsync(request, cancellationToken);
        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        if (!response.IsSuccessStatusCode) throw CreateException(response.StatusCode, body);
        var root = JsonNode.Parse(body)?.AsObject();
        return root?["choices"]?[0]?["message"]?["content"]?.GetValue<string>() ?? "{}";
    }

    private async Task<ProviderResult> CompleteOnceAsync(ProviderConfig config, JsonArray messages,
        JsonArray tools, Func<string, Task> onDelta, CancellationToken cancellationToken)
    {
        var body = new JsonObject
        {
            ["model"] = config.Model,
            ["messages"] = messages.DeepClone(),
            ["max_tokens"] = config.MaxTokens,
            ["temperature"] = config.Temperature,
            ["stream"] = true
        };
        if (tools.Count > 0)
        {
            body["tools"] = tools.DeepClone();
            body["tool_choice"] = "auto";
        }
        else body["tool_choice"] = "none";
        using var request = CreateRequest(config, body);
        using var response = await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync(cancellationToken);
            throw CreateException(response.StatusCode, error);
        }

        if (!response.Content.Headers.ContentType?.MediaType?.Contains("event-stream", StringComparison.OrdinalIgnoreCase) == true)
        {
            return await ParseNonStreamingAsync(response, onDelta, cancellationToken);
        }

        var content = new StringBuilder();
        var pending = new Dictionary<int, PendingToolCall>();
        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var reader = new StreamReader(stream);
        while (!reader.EndOfStream)
        {
            var line = await reader.ReadLineAsync(cancellationToken);
            if (string.IsNullOrWhiteSpace(line) || !line.StartsWith("data:", StringComparison.Ordinal)) continue;
            var data = line[5..].Trim();
            if (data == "[DONE]") break;
            var root = JsonNode.Parse(data)?.AsObject();
            var delta = root?["choices"]?[0]?["delta"] as JsonObject;
            if (delta is null) continue;
            var text = delta["content"]?.GetValue<string>();
            if (!string.IsNullOrEmpty(text))
            {
                content.Append(text);
                await onDelta(text);
            }
            if (delta["tool_calls"] is JsonArray calls)
            {
                foreach (var node in calls)
                {
                    if (node is not JsonObject call) continue;
                    var index = call["index"]?.GetValue<int>() ?? 0;
                    if (!pending.TryGetValue(index, out var value)) pending[index] = value = new PendingToolCall();
                    value.Id += call["id"]?.GetValue<string>() ?? string.Empty;
                    if (call["function"] is JsonObject function)
                    {
                        value.Name += function["name"]?.GetValue<string>() ?? string.Empty;
                        value.Arguments.Append(function["arguments"]?.GetValue<string>() ?? string.Empty);
                    }
                }
            }
        }
        return new ProviderResult(content.ToString(), pending.OrderBy(x => x.Key).Select(x => x.Value.Build()).ToList());
    }

    private static async Task<ProviderResult> ParseNonStreamingAsync(HttpResponseMessage response,
        Func<string, Task> onDelta, CancellationToken cancellationToken)
    {
        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        var message = JsonNode.Parse(body)?["choices"]?[0]?["message"] as JsonObject;
        var content = message?["content"]?.GetValue<string>() ?? string.Empty;
        if (content.Length > 0) await onDelta(content);
        var calls = new List<ToolCall>();
        if (message?["tool_calls"] is JsonArray toolCalls)
        {
            foreach (var node in toolCalls)
            {
                var call = node?.AsObject();
                var function = call?["function"]?.AsObject();
                var arguments = JsonNode.Parse(function?["arguments"]?.GetValue<string>() ?? "{}") as JsonObject ?? new JsonObject();
                calls.Add(new ToolCall(call?["id"]?.GetValue<string>() ?? Guid.NewGuid().ToString("N"),
                    function?["name"]?.GetValue<string>() ?? string.Empty, arguments));
            }
        }
        return new ProviderResult(content, calls);
    }

    private static HttpRequestMessage CreateRequest(ProviderConfig config, JsonObject body)
    {
        var request = new HttpRequestMessage(HttpMethod.Post, config.BaseUrl)
        {
            Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json")
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", config.ApiKey);
        request.Headers.UserAgent.ParseAdd("GitLocal-Agent/2.0");
        return request;
    }

    private static ProviderException CreateException(HttpStatusCode status, string body)
    {
        var message = body.Length > 1000 ? body[..1000] : body;
        return new ProviderException((int)status, $"AI服务返回HTTP {(int)status}: {message}");
    }

    private sealed class PendingToolCall
    {
        public string Id = string.Empty;
        public string Name = string.Empty;
        public StringBuilder Arguments { get; } = new();
        public ToolCall Build()
        {
            JsonObject arguments;
            try { arguments = JsonNode.Parse(Arguments.Length == 0 ? "{}" : Arguments.ToString()) as JsonObject ?? new JsonObject(); }
            catch { arguments = new JsonObject(); }
            return new ToolCall(string.IsNullOrEmpty(Id) ? Guid.NewGuid().ToString("N") : Id, Name, arguments);
        }
    }
}
