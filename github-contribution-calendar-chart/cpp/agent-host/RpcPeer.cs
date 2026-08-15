using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.IO.Pipes;
using System.Text.Json.Nodes;

namespace GitLocal.Agent;

internal sealed class RpcPeer : IAsyncDisposable
{
    private const int MaxFrameLength = 8 * 1024 * 1024;
    private readonly Stream _stream;
    private readonly SemaphoreSlim _writeLock = new(1, 1);
    private readonly ConcurrentDictionary<string, TaskCompletionSource<JsonNode?>> _pending = new();
    private long _nextId;

    public Func<string, JsonObject, Task<JsonNode?>>? RequestHandler { get; set; }

    public RpcPeer(Stream stream) => _stream = stream;

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        var header = new byte[4];
        while (!cancellationToken.IsCancellationRequested)
        {
            if (!await ReadExactlyAsync(header, cancellationToken)) break;
            var length = BinaryPrimitives.ReadInt32LittleEndian(header);
            if (length <= 0 || length > MaxFrameLength) throw new InvalidDataException("Invalid RPC frame length.");
            var payload = new byte[length];
            if (!await ReadExactlyAsync(payload, cancellationToken)) break;
            var message = JsonNode.Parse(payload)?.AsObject() ?? throw new InvalidDataException("Invalid RPC JSON.");
            var method = ReadString(message["method"]);
            if (method is not null)
            {
                var parameters = message["params"] as JsonObject ?? new JsonObject();
                var id = ReadString(message["id"]);
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var result = RequestHandler is null ? null : await RequestHandler(method, parameters);
                        if (id is not null) await SendResponseAsync(id, result, null, cancellationToken);
                    }
                    catch (Exception ex)
                    {
                        if (id is not null) await SendResponseAsync(id, null, ex.Message, cancellationToken);
                    }
                }, CancellationToken.None);
            }
            else if (ReadString(message["id"]) is string responseId)
            {
                if (_pending.TryRemove(responseId, out var completion))
                {
                    if (message["error"] is JsonObject error)
                        completion.TrySetException(new InvalidOperationException(ReadString(error["message"]) ?? "RPC error"));
                    else completion.TrySetResult(message["result"]?.DeepClone());
                }
            }
        }
        foreach (var pending in _pending.Values) pending.TrySetException(new EndOfStreamException("RPC peer disconnected."));
    }

    public async Task<JsonNode?> CallAsync(string method, JsonObject parameters, CancellationToken cancellationToken)
    {
        var id = "a-" + Interlocked.Increment(ref _nextId);
        var completion = new TaskCompletionSource<JsonNode?>(TaskCreationOptions.RunContinuationsAsynchronously);
        _pending[id] = completion;
        using var registration = cancellationToken.Register(() => completion.TrySetCanceled(cancellationToken));
        await SendAsync(new JsonObject
        {
            ["jsonrpc"] = "2.0", ["id"] = id, ["method"] = method, ["params"] = parameters
        }, cancellationToken);
        return await completion.Task;
    }

    public Task NotifyAsync(string method, JsonObject parameters, CancellationToken cancellationToken = default) =>
        SendAsync(new JsonObject { ["jsonrpc"] = "2.0", ["method"] = method, ["params"] = parameters }, cancellationToken);

    private Task SendResponseAsync(string id, JsonNode? result, string? error, CancellationToken cancellationToken)
    {
        var response = new JsonObject { ["jsonrpc"] = "2.0", ["id"] = id };
        if (error is null) response["result"] = result?.DeepClone();
        else response["error"] = new JsonObject { ["code"] = -32000, ["message"] = error };
        return SendAsync(response, cancellationToken);
    }

    private async Task SendAsync(JsonObject message, CancellationToken cancellationToken)
    {
        var payload = System.Text.Encoding.UTF8.GetBytes(message.ToJsonString());
        if (payload.Length > MaxFrameLength) throw new InvalidDataException("RPC frame is too large.");
        var header = new byte[4];
        BinaryPrimitives.WriteInt32LittleEndian(header, payload.Length);
        await _writeLock.WaitAsync(cancellationToken);
        try
        {
            await _stream.WriteAsync(header, cancellationToken);
            await _stream.WriteAsync(payload, cancellationToken);
            await _stream.FlushAsync(cancellationToken);
        }
        finally { _writeLock.Release(); }
    }

    private async Task<bool> ReadExactlyAsync(byte[] buffer, CancellationToken cancellationToken)
    {
        var offset = 0;
        while (offset < buffer.Length)
        {
            var read = await _stream.ReadAsync(buffer.AsMemory(offset), cancellationToken);
            if (read == 0) return false;
            offset += read;
        }
        return true;
    }

    private static string? ReadString(JsonNode? node)
    {
        if (node is null) return null;
        try { return node.GetValue<string>(); }
        catch (InvalidOperationException) { return node.ToJsonString(); }
    }

    public async ValueTask DisposeAsync()
    {
        await _stream.DisposeAsync();
        _writeLock.Dispose();
    }
}
