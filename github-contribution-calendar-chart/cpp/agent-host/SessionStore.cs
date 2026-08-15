using Microsoft.Data.Sqlite;
using System.Text.Json.Nodes;

namespace GitLocal.Agent;

internal sealed record StoredMessage(string Role, string Content, string? ToolCallId,
                                     string? Name, JsonArray? ToolCalls, long Id = 0);
internal sealed record StoredCheckpoint(string Summary, long ThroughMessageId);

internal sealed class SessionStore
{
    private readonly string _connectionString;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public SessionStore(string databasePath)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(databasePath)!);
        _connectionString = new SqliteConnectionStringBuilder { DataSource = databasePath }.ToString();
    }

    public async Task InitializeAsync()
    {
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = """
                PRAGMA journal_mode=WAL;
                PRAGMA foreign_keys=ON;
                CREATE TABLE IF NOT EXISTS sessions(
                    id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS messages(
                    id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, role TEXT NOT NULL,
                    content TEXT NOT NULL, tool_call_id TEXT, name TEXT, tool_calls_json TEXT,
                    created_at TEXT NOT NULL, FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE);
                CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id, id);
                CREATE TABLE IF NOT EXISTS checkpoints(
                    session_id TEXT PRIMARY KEY, summary TEXT NOT NULL, through_message_id INTEGER NOT NULL,
                    updated_at TEXT NOT NULL, FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE);
                CREATE TABLE IF NOT EXISTS memory_items(
                    id INTEGER PRIMARY KEY AUTOINCREMENT, scope TEXT NOT NULL, content TEXT NOT NULL,
                    created_at TEXT NOT NULL, last_used_at TEXT NOT NULL);
                """;
            await command.ExecuteNonQueryAsync();
        }
        finally { _gate.Release(); }
    }

    public async Task EnsureSessionAsync(string sessionId, string firstMessage)
    {
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = """
                INSERT INTO sessions(id,title,created_at,updated_at) VALUES($id,$title,$now,$now)
                ON CONFLICT(id) DO UPDATE SET updated_at=$now;
                """;
            command.Parameters.AddWithValue("$id", sessionId);
            command.Parameters.AddWithValue("$title", CreateTitle(firstMessage));
            command.Parameters.AddWithValue("$now", DateTimeOffset.UtcNow.ToString("O"));
            await command.ExecuteNonQueryAsync();
        }
        finally { _gate.Release(); }
    }

    public async Task AddMessageAsync(string sessionId, StoredMessage message)
    {
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = """
                INSERT INTO messages(session_id,role,content,tool_call_id,name,tool_calls_json,created_at)
                VALUES($session,$role,$content,$toolCallId,$name,$toolCalls,$now);
                UPDATE sessions SET updated_at=$now WHERE id=$session;
                """;
            command.Parameters.AddWithValue("$session", sessionId);
            command.Parameters.AddWithValue("$role", message.Role);
            command.Parameters.AddWithValue("$content", message.Content);
            command.Parameters.AddWithValue("$toolCallId", (object?)message.ToolCallId ?? DBNull.Value);
            command.Parameters.AddWithValue("$name", (object?)message.Name ?? DBNull.Value);
            command.Parameters.AddWithValue("$toolCalls", message.ToolCalls is null ? DBNull.Value : message.ToolCalls.ToJsonString());
            command.Parameters.AddWithValue("$now", DateTimeOffset.UtcNow.ToString("O"));
            await command.ExecuteNonQueryAsync();
        }
        finally { _gate.Release(); }
    }

    public async Task<List<StoredMessage>> GetMessagesAsync(string sessionId)
    {
        var messages = new List<StoredMessage>();
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = "SELECT id,role,content,tool_call_id,name,tool_calls_json FROM messages WHERE session_id=$id ORDER BY id";
            command.Parameters.AddWithValue("$id", sessionId);
            await using var reader = await command.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                var calls = reader.IsDBNull(5) ? null : JsonNode.Parse(reader.GetString(5))?.AsArray();
                messages.Add(new StoredMessage(reader.GetString(1), reader.GetString(2),
                    reader.IsDBNull(3) ? null : reader.GetString(3), reader.IsDBNull(4) ? null : reader.GetString(4), calls,
                    reader.GetInt64(0)));
            }
        }
        finally { _gate.Release(); }
        return messages;
    }

    public async Task<JsonArray> ListSessionsAsync()
    {
        var result = new JsonArray();
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = "SELECT id,title,updated_at FROM sessions ORDER BY updated_at DESC LIMIT 100";
            await using var reader = await command.ExecuteReaderAsync();
            while (await reader.ReadAsync()) result.Insert(result.Count, new JsonObject
            {
                ["id"] = reader.GetString(0), ["title"] = reader.GetString(1), ["updatedAt"] = reader.GetString(2)
            });
        }
        finally { _gate.Release(); }
        return result;
    }

    public async Task DeleteSessionAsync(string sessionId)
    {
        await ExecuteAsync("DELETE FROM sessions WHERE id=$value", sessionId);
    }

    public async Task AddMemoryAsync(string scope, string content)
    {
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = "INSERT INTO memory_items(scope,content,created_at,last_used_at) VALUES($scope,$content,$now,$now)";
            command.Parameters.AddWithValue("$scope", scope);
            command.Parameters.AddWithValue("$content", content);
            command.Parameters.AddWithValue("$now", DateTimeOffset.UtcNow.ToString("O"));
            await command.ExecuteNonQueryAsync();
        }
        finally { _gate.Release(); }
    }

    public async Task<List<string>> FindMemoriesAsync(string scope, string query)
    {
        var result = new List<string>();
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = """
                SELECT content FROM memory_items
                WHERE scope='global' OR scope=$scope
                ORDER BY last_used_at DESC LIMIT 20
                """;
            command.Parameters.AddWithValue("$scope", scope);
            await using var reader = await command.ExecuteReaderAsync();
            while (await reader.ReadAsync()) result.Add(reader.GetString(0));
        }
        finally { _gate.Release(); }
        return result;
    }

    public async Task SaveCheckpointAsync(string sessionId, string summary, long throughMessageId)
    {
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = """
                INSERT INTO checkpoints(session_id,summary,through_message_id,updated_at) VALUES($id,$summary,$through,$now)
                ON CONFLICT(session_id) DO UPDATE SET summary=$summary,through_message_id=$through,updated_at=$now;
                """;
            command.Parameters.AddWithValue("$id", sessionId);
            command.Parameters.AddWithValue("$summary", summary);
            command.Parameters.AddWithValue("$through", throughMessageId);
            command.Parameters.AddWithValue("$now", DateTimeOffset.UtcNow.ToString("O"));
            await command.ExecuteNonQueryAsync();
        }
        finally { _gate.Release(); }
    }

    public async Task<StoredCheckpoint?> GetCheckpointAsync(string sessionId)
    {
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = "SELECT summary,through_message_id FROM checkpoints WHERE session_id=$id";
            command.Parameters.AddWithValue("$id", sessionId);
            await using var reader = await command.ExecuteReaderAsync();
            return await reader.ReadAsync()
                ? new StoredCheckpoint(reader.GetString(0), reader.GetInt64(1))
                : null;
        }
        finally { _gate.Release(); }
    }

    private async Task ExecuteAsync(string sql, string value)
    {
        await _gate.WaitAsync();
        try
        {
            await using var connection = new SqliteConnection(_connectionString);
            await connection.OpenAsync();
            var command = connection.CreateCommand();
            command.CommandText = sql;
            command.Parameters.AddWithValue("$value", value);
            await command.ExecuteNonQueryAsync();
        }
        finally { _gate.Release(); }
    }

    private static string CreateTitle(string message)
    {
        var compact = string.Join(' ', message.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries));
        return compact.Length <= 32 ? compact : compact[..32] + "...";
    }
}
