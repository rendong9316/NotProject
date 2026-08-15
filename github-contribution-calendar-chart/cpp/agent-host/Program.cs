using System.IO.Pipes;
using System.Text.Json.Nodes;
using GitLocal.Agent;

var arguments = ParseArguments(args);
if (!arguments.TryGetValue("pipe", out var pipeName) || !arguments.TryGetValue("data-dir", out var dataDirectory))
{
    Console.Error.WriteLine("Usage: GitLocal.Agent --pipe NAME --data-dir PATH");
    return 2;
}

using var shutdown = new CancellationTokenSource();
Console.CancelKeyPress += (_, eventArgs) => { eventArgs.Cancel = true; shutdown.Cancel(); };
var pipe = new NamedPipeClientStream(".", pipeName, PipeDirection.InOut, PipeOptions.Asynchronous);
await pipe.ConnectAsync(5000, shutdown.Token);
await using var rpc = new RpcPeer(pipe);
var store = new SessionStore(Path.Combine(dataDirectory, "agent", "agent.db"));
await store.InitializeAsync();
var runtime = new AgentRuntime(rpc, store);

rpc.RequestHandler = async (method, parameters) =>
{
    switch (method)
    {
        case "initialize":
        {
            if (parameters["protocolVersion"]?.GetValue<int>() != 1) throw new InvalidOperationException("不支持的协议版本");
            var config = parameters["config"]?.AsObject() ?? throw new InvalidOperationException("缺少AI配置");
            runtime.Initialize(new ProviderConfig(
                config["baseUrl"]?.GetValue<string>() ?? throw new InvalidOperationException("缺少baseUrl"),
                config["model"]?.GetValue<string>() ?? throw new InvalidOperationException("缺少model"),
                config["apiKey"]?.GetValue<string>() ?? string.Empty,
                config["maxTokens"]?.GetValue<int>() ?? 2048,
                config["contextWindowTokens"]?.GetValue<int>() ?? 32768,
                config["temperature"]?.GetValue<double>() ?? 0.3),
                parameters["tools"]?.AsArray() ?? new JsonArray());
            return new JsonObject { ["protocolVersion"] = 1, ["status"] = "ready", ["pid"] = Environment.ProcessId };
        }
        case "health":
            return new JsonObject { ["status"] = "ok", ["protocolVersion"] = 1 };
        case "shutdown":
            _ = Task.Run(async () =>
            {
                await Task.Delay(100);
                shutdown.Cancel();
            });
            return new JsonObject { ["accepted"] = true };
        case "chat.start":
            runtime.StartTurn(parameters["sessionId"]?.GetValue<string>() ?? throw new InvalidOperationException("缺少sessionId"),
                parameters["turnId"]?.GetValue<string>() ?? throw new InvalidOperationException("缺少turnId"),
                parameters["message"]?.GetValue<string>() ?? string.Empty,
                parameters["context"]?.AsObject() ?? new JsonObject());
            return new JsonObject { ["accepted"] = true };
        case "chat.cancel":
            return new JsonObject { ["cancelled"] = runtime.Cancel(parameters["turnId"]?.GetValue<string>() ?? string.Empty) };
        case "session.list":
            return new JsonObject { ["sessions"] = await store.ListSessionsAsync() };
        case "session.history":
        {
            var values = new JsonArray();
            foreach (var message in await store.GetMessagesAsync(parameters["sessionId"]?.GetValue<string>() ?? string.Empty))
            {
                if (message.Role is not ("user" or "assistant")) continue;
                values.Insert(values.Count, new JsonObject { ["role"] = message.Role, ["content"] = message.Content });
            }
            return new JsonObject { ["messages"] = values };
        }
        case "session.delete":
            await store.DeleteSessionAsync(parameters["sessionId"]?.GetValue<string>() ?? string.Empty);
            return new JsonObject { ["deleted"] = true };
        default:
            throw new InvalidOperationException("未知方法: " + method);
    }
};

try { await rpc.RunAsync(shutdown.Token); }
catch (OperationCanceledException) { }
return 0;

static Dictionary<string, string> ParseArguments(string[] values)
{
    var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    for (var i = 0; i + 1 < values.Length; i += 2)
        if (values[i].StartsWith("--", StringComparison.Ordinal)) result[values[i][2..]] = values[i + 1];
    return result;
}
