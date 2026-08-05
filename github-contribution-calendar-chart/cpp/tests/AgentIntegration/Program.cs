using System.Buffers.Binary;
using System.Diagnostics;
using System.IO.Pipes;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Nodes;

var root = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", ".."));
var agentExecutable = Path.Combine(root, "build", "agent", "GitLocal.Agent.exe");
if (!File.Exists(agentExecutable)) throw new FileNotFoundException("Publish the sidecar before running integration tests.", agentExecutable);

var testDirectory = Path.Combine(Path.GetTempPath(), "GitLocalAgentTests", Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(testDirectory);
var fakeProvider = new FakeProvider();
await fakeProvider.StartAsync();
var pipeName = "GitLocal.Agent.Test." + Guid.NewGuid().ToString("N");
await using var pipe = new NamedPipeServerStream(pipeName, PipeDirection.InOut, 1,
    PipeTransmissionMode.Byte, PipeOptions.Asynchronous);
using var process = Process.Start(new ProcessStartInfo
{
    FileName = agentExecutable,
    ArgumentList = { "--pipe", pipeName, "--data-dir", testDirectory },
    UseShellExecute = false,
    CreateNoWindow = true
}) ?? throw new InvalidOperationException("Could not start sidecar.");

try
{
    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(20));
    await pipe.WaitForConnectionAsync(timeout.Token);
    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["id"] = "init", ["method"] = "initialize",
        ["params"] = new JsonObject
        {
            ["protocolVersion"] = 1,
            ["config"] = new JsonObject
            {
                ["baseUrl"] = fakeProvider.Url, ["model"] = "fake-model", ["apiKey"] = "test-key",
                ["maxTokens"] = 256, ["contextWindowTokens"] = 32768, ["temperature"] = 0.1
            },
            ["tools"] = new JsonArray(new JsonObject
            {
                ["type"] = "function",
                ["function"] = new JsonObject
                {
                    ["name"] = "get_repo_stats", ["description"] = "test",
                    ["parameters"] = new JsonObject { ["type"] = "object", ["properties"] = new JsonObject() }
                }
            })
        }
    }, timeout.Token);
    var initialized = await ReadFrameAsync(pipe, timeout.Token);
    Assert(initialized["id"]?.GetValue<string>() == "init", "initialize response missing");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["method"] = "chat.start",
        ["params"] = new JsonObject
        {
            ["sessionId"] = "integration", ["turnId"] = "turn-1", ["message"] = "分析2026年仓库",
            ["context"] = new JsonObject { ["activeYear"] = 2026, ["projectScope"] = "integration" }
        }
    }, timeout.Token);

    var text = new StringBuilder();
    var toolCalled = false;
    var toolStarted = false;
    var toolCompleted = false;
    var completed = false;
    while (!completed)
    {
        var message = await ReadFrameAsync(pipe, timeout.Token);
        var method = message["method"]?.GetValue<string>();
        if (method == "chat.tool_started")
            toolStarted = message["params"]?["name"]?.GetValue<string>() == "get_repo_stats";
        else if (method == "chat.tool_completed")
            toolCompleted = message["params"]?["name"]?.GetValue<string>() == "get_repo_stats";
        else if (method == "tool.execute")
        {
            toolCalled = message["params"]?["name"]?.GetValue<string>() == "get_repo_stats";
            await WriteFrameAsync(pipe, new JsonObject
            {
                ["jsonrpc"] = "2.0", ["id"] = message["id"]?.GetValue<string>(),
                ["result"] = new JsonObject { ["ok"] = true, ["repositories"] = new JsonObject { ["demo"] = 42 } }
            }, timeout.Token);
        }
        else if (method == "chat.delta") text.Append(message["params"]?["text"]?.GetValue<string>());
        else if (method == "chat.completed") completed = true;
        else if (method == "chat.failed") throw new InvalidOperationException(message["params"]?["message"]?.GetValue<string>());
    }

    Assert(toolCalled, "sidecar did not request the expected tool");
    Assert(toolStarted && toolCompleted, "tool lifecycle notifications were incomplete");
    Assert(text.ToString() == "测试完成", "streamed response was not assembled");
    Assert(File.Exists(Path.Combine(testDirectory, "agent", "agent.db")), "conversation database was not created");
    Assert(fakeProvider.RequestCount == 2, "agent loop did not continue after tool result");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["method"] = "chat.start",
        ["params"] = new JsonObject
        {
            ["sessionId"] = "time-route", ["turnId"] = "turn-time",
            ["message"] = "请分析我全年提交时间偏好，最早和最晚分别是什么时候",
            ["context"] = new JsonObject { ["activeYear"] = 2026, ["projectScope"] = "integration" }
        }
    }, timeout.Token);
    var timeRouteText = new StringBuilder();
    var timeRouteToolCalled = false;
    var timeRouteCompleted = false;
    while (!timeRouteCompleted)
    {
        var routeMessage = await ReadFrameAsync(pipe, timeout.Token);
        var routeMethod = routeMessage["method"]?.GetValue<string>();
        if (routeMethod == "tool.execute")
        {
            timeRouteToolCalled = routeMessage["params"]?["name"]?.GetValue<string>() == "get_time_preferences";
            await WriteFrameAsync(pipe, new JsonObject
            {
                ["jsonrpc"] = "2.0", ["id"] = routeMessage["id"]?.GetValue<string>(),
                ["result"] = new JsonObject
                {
                    ["ok"] = true, ["totalCommits"] = 5,
                    ["hours"] = new JsonObject { ["09"] = 3, ["13"] = 2 },
                    ["topHours"] = new JsonArray(
                        new JsonObject { ["hour"] = "09", ["count"] = 3 },
                        new JsonObject { ["hour"] = "13", ["count"] = 2 }),
                    ["earliest"] = new JsonObject { ["date"] = "2026-01-01", ["time"] = "09:01:00" },
                    ["latest"] = new JsonObject { ["date"] = "2026-12-31", ["time"] = "13:59:00" }
                }
            }, timeout.Token);
        }
        else if (routeMethod == "chat.delta") timeRouteText.Append(routeMessage["params"]?["text"]?.GetValue<string>());
        else if (routeMethod == "chat.completed") timeRouteCompleted = true;
        else if (routeMethod == "chat.failed") throw new InvalidOperationException("time preference route failed");
    }
    Assert(timeRouteToolCalled, "time preference intent did not route to the aggregate tool");
    Assert(timeRouteText.ToString().Contains("最常提交时段：09:00-09:59", StringComparison.Ordinal),
        "time preference route did not format aggregate tool results");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["method"] = "chat.start",
        ["params"] = new JsonObject
        {
            ["sessionId"] = "repository-route", ["turnId"] = "turn-repository",
            ["message"] = "对于RadarView项目的开发，你觉得我的提交状况怎样",
            ["context"] = new JsonObject
            {
                ["activeYear"] = 2026, ["projectScope"] = "integration",
                ["selectedRepositories"] = new JsonArray("RadarView_BuildByTauri")
            }
        }
    }, timeout.Token);
    var repositoryRouteText = new StringBuilder();
    var repositoryRouteToolCalled = false;
    var repositoryRouteCompleted = false;
    while (!repositoryRouteCompleted)
    {
        var routeMessage = await ReadFrameAsync(pipe, timeout.Token);
        var routeMethod = routeMessage["method"]?.GetValue<string>();
        if (routeMethod == "tool.execute")
        {
            repositoryRouteToolCalled = routeMessage["params"]?["name"]?.GetValue<string>() == "get_repository_overview";
            await WriteFrameAsync(pipe, new JsonObject
            {
                ["jsonrpc"] = "2.0", ["id"] = routeMessage["id"]?.GetValue<string>(),
                ["result"] = new JsonObject
                {
                    ["ok"] = true, ["repository"] = "RadarView_BuildByTauri", ["year"] = 2026,
                    ["totalCommits"] = 137, ["activeDays"] = 42, ["longestStreak"] = 9,
                    ["topAuthors"] = new JsonArray(new JsonObject { ["author"] = "tester", ["count"] = 137 }),
                    ["topHours"] = new JsonArray(new JsonObject { ["hour"] = "10", ["count"] = 28 }),
                    ["months"] = new JsonArray(0, 0, 0, 0, 12, 35, 30, 20, 10, 8, 7, 15),
                    ["weekdays"] = new JsonArray(10, 30, 22, 20, 18, 17, 20),
                    ["peakDays"] = new JsonArray(new JsonObject { ["date"] = "2026-06-17", ["count"] = 8 }),
                    ["firstCommit"] = new JsonObject { ["date"] = "2026-05-01", ["time"] = "10:00:00" },
                    ["lastCommit"] = new JsonObject { ["date"] = "2026-08-05", ["time"] = "14:00:00" }
                }
            }, timeout.Token);
        }
        else if (routeMethod == "chat.delta") repositoryRouteText.Append(routeMessage["params"]?["text"]?.GetValue<string>());
        else if (routeMethod == "chat.completed") repositoryRouteCompleted = true;
        else if (routeMethod == "chat.failed") throw new InvalidOperationException("repository overview route failed");
    }
    Assert(repositoryRouteToolCalled, "repository overview intent did not route to the aggregate tool");
    Assert(repositoryRouteText.ToString().Contains("RadarView_BuildByTauri 在 2026 年共有 137 次提交", StringComparison.Ordinal),
        "repository overview route did not format aggregate tool results");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["method"] = "chat.start",
        ["params"] = new JsonObject
        {
            ["sessionId"] = "repeat", ["turnId"] = "turn-repeat", ["message"] = "测试重复工具保护",
            ["context"] = new JsonObject { ["activeYear"] = 2026, ["projectScope"] = "integration" }
        }
    }, timeout.Token);
    var repeatText = new StringBuilder();
    var repeatCompleted = false;
    while (!repeatCompleted)
    {
        var repeatMessage = await ReadFrameAsync(pipe, timeout.Token);
        var repeatMethod = repeatMessage["method"]?.GetValue<string>();
        if (repeatMethod == "tool.execute")
        {
            await WriteFrameAsync(pipe, new JsonObject
            {
                ["jsonrpc"] = "2.0", ["id"] = repeatMessage["id"]?.GetValue<string>(),
                ["result"] = new JsonObject { ["ok"] = true, ["hours"] = new JsonObject { ["09"] = 3 } }
            }, timeout.Token);
        }
        else if (repeatMethod == "chat.delta") repeatText.Append(repeatMessage["params"]?["text"]?.GetValue<string>());
        else if (repeatMethod == "chat.completed") repeatCompleted = true;
        else if (repeatMethod == "chat.failed") throw new InvalidOperationException("repeated tool turn failed");
    }
    Assert(repeatText.ToString().Contains("工具调用", StringComparison.Ordinal),
        "repeated tool calls did not produce a final fallback response");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["id"] = "history", ["method"] = "session.history",
        ["params"] = new JsonObject { ["sessionId"] = "integration" }
    }, timeout.Token);
    var history = await ReadFrameAsync(pipe, timeout.Token);
    Assert(history["result"]?["messages"]?.AsArray().Count >= 2, "persisted conversation history was not returned");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["id"] = "list-before-delete", ["method"] = "session.list",
        ["params"] = new JsonObject()
    }, timeout.Token);
    var listBeforeDelete = await ReadFrameAsync(pipe, timeout.Token);
    Assert(listBeforeDelete["result"]?["sessions"]?.AsArray()
        .Any(item => item?["id"]?.GetValue<string>() == "integration") == true,
        "persisted conversation was missing from the session list");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["id"] = "delete", ["method"] = "session.delete",
        ["params"] = new JsonObject { ["sessionId"] = "integration" }
    }, timeout.Token);
    var deleted = await ReadFrameAsync(pipe, timeout.Token);
    Assert(deleted["result"]?["deleted"]?.GetValue<bool>() == true, "session delete was not acknowledged");

    await WriteFrameAsync(pipe, new JsonObject
    {
        ["jsonrpc"] = "2.0", ["id"] = "history-after-delete", ["method"] = "session.history",
        ["params"] = new JsonObject { ["sessionId"] = "integration" }
    }, timeout.Token);
    var historyAfterDelete = await ReadFrameAsync(pipe, timeout.Token);
    Assert(historyAfterDelete["result"]?["messages"]?.AsArray().Count == 0,
        "deleted conversation history is still present");

    await WriteFrameAsync(pipe, new JsonObject { ["jsonrpc"] = "2.0", ["method"] = "shutdown", ["params"] = new JsonObject() }, timeout.Token);
    Assert(process.WaitForExit(5000), "sidecar did not shut down gracefully");
    Assert(process.ExitCode == 0, "sidecar shutdown exit code was not zero");
    Console.WriteLine("PASS sidecar named-pipe/tool/stream/persistence integration");
}
finally
{
    await fakeProvider.DisposeAsync();
    pipe.Dispose();
    if (!process.WaitForExit(5000)) process.Kill(entireProcessTree: true);
    try { Directory.Delete(testDirectory, recursive: true); } catch { }
}

static void Assert(bool condition, string message)
{
    if (!condition) throw new InvalidOperationException("FAIL: " + message);
}

static async Task WriteFrameAsync(Stream stream, JsonObject message, CancellationToken cancellationToken)
{
    var payload = Encoding.UTF8.GetBytes(message.ToJsonString());
    var header = new byte[4];
    BinaryPrimitives.WriteInt32LittleEndian(header, payload.Length);
    await stream.WriteAsync(header, cancellationToken);
    await stream.WriteAsync(payload, cancellationToken);
    await stream.FlushAsync(cancellationToken);
}

static async Task<JsonObject> ReadFrameAsync(Stream stream, CancellationToken cancellationToken)
{
    var header = await ReadExactlyAsync(stream, 4, cancellationToken);
    var length = BinaryPrimitives.ReadInt32LittleEndian(header);
    var payload = await ReadExactlyAsync(stream, length, cancellationToken);
    return JsonNode.Parse(payload)?.AsObject() ?? throw new InvalidDataException("Invalid JSON frame.");
}

static async Task<byte[]> ReadExactlyAsync(Stream stream, int length, CancellationToken cancellationToken)
{
    var result = new byte[length];
    var offset = 0;
    while (offset < length)
    {
        var read = await stream.ReadAsync(result.AsMemory(offset), cancellationToken);
        if (read == 0) throw new EndOfStreamException();
        offset += read;
    }
    return result;
}

sealed class FakeProvider : IAsyncDisposable
{
    private readonly TcpListener _listener = new(IPAddress.Loopback, 0);
    private readonly CancellationTokenSource _shutdown = new();
    private Task? _server;
    private int _requestCount;
    public int RequestCount => _requestCount;
    public string Url { get; private set; } = string.Empty;

    public Task StartAsync()
    {
        _listener.Start();
        var port = ((IPEndPoint)_listener.LocalEndpoint).Port;
        Url = $"http://127.0.0.1:{port}/v1/chat/completions";
        _server = Task.Run(ServerLoopAsync);
        return Task.CompletedTask;
    }

    private async Task ServerLoopAsync()
    {
        while (!_shutdown.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await _listener.AcceptTcpClientAsync(_shutdown.Token); }
            catch (OperationCanceledException) { break; }
            _ = Task.Run(() => HandleAsync(client));
        }
    }

    private async Task HandleAsync(TcpClient client)
    {
        await using var stream = client.GetStream();
        using var reader = new StreamReader(stream, Encoding.ASCII, false, 4096, leaveOpen: true);
        var contentLength = 0;
        string? line;
        while (!string.IsNullOrEmpty(line = await reader.ReadLineAsync()))
            if (line.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase))
                contentLength = int.Parse(line.Split(':', 2)[1].Trim());
        if (contentLength > 0)
        {
            var body = new char[contentLength];
            await reader.ReadBlockAsync(body, 0, body.Length);
        }
        var requestNumber = Interlocked.Increment(ref _requestCount);
        var sse = requestNumber == 1
            ? "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"function\":{\"name\":\"get_repo_stats\",\"arguments\":\"{\\\"year\\\":2026}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n"
            : requestNumber == 2
                ? "data: {\"choices\":[{\"delta\":{\"content\":\"测试\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"完成\"}}]}\n\ndata: [DONE]\n\n"
                : "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"repeat-call\",\"function\":{\"name\":\"get_hourly_dist\",\"arguments\":\"{\\\"date\\\":\\\"2026-06-17\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n";
        var payload = Encoding.UTF8.GetBytes(sse);
        var headers = Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {payload.Length}\r\nConnection: close\r\n\r\n");
        await stream.WriteAsync(headers);
        await stream.WriteAsync(payload);
        client.Dispose();
    }

    public async ValueTask DisposeAsync()
    {
        _shutdown.Cancel();
        _listener.Stop();
        if (_server is not null) try { await _server; } catch { }
        _shutdown.Dispose();
    }
}
