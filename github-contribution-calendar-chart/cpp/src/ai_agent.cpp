#include "ai_agent.h"

#include "ai_tools.h"
#include "cache_store.h"
#include "config.h"
#include "json.h"
#include "platform.h"
#include "state.h"

#include <windows.h>
#include <sddl.h>
#include <commctrl.h>
#include <algorithm>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cwctype>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

namespace {

constexpr DWORD kMaxFrameSize = 8 * 1024 * 1024;
constexpr int kProtocolVersion = 1;

struct ConversationSummary {
    std::wstring id;
    std::wstring title;
};

enum class UiEventKind { Ready, History, SessionList, Delta, Completed, Failed, ToolStarted, ToolCompleted, Disconnected };

struct UiEvent {
    UiEventKind kind = UiEventKind::Failed;
    std::wstring turnId;
    std::wstring text;
    std::wstring detail;
    std::deque<ChatMessage> history;
    std::vector<ConversationSummary> sessions;
};

HANDLE g_pipe = INVALID_HANDLE_VALUE;
HANDLE g_process = nullptr;
HANDLE g_job = nullptr;
std::thread g_pipeThread;
std::thread g_writerThread;
std::mutex g_outboundMutex;
std::condition_variable g_outboundReady;
std::deque<std::string> g_outboundFrames;
std::mutex g_contextMutex;
std::atomic<bool> g_shutdown{false};
std::atomic<bool> g_processRunning{false};
std::atomic<bool> g_ready{false};
AnalyticsContext g_analyticsContext;
HFONT g_agentFont = nullptr;
std::vector<ConversationSummary> g_conversations;

constexpr int kAgentHeaderHeight = 48;
constexpr int kAgentInputHeight = 88;
constexpr int kAgentInset = 12;

std::wstring NewId(const wchar_t* prefix) {
    GUID value = {};
    CoCreateGuid(&value);
    wchar_t buffer[64] = {};
    StringFromGUID2(value, buffer, static_cast<int>(std::size(buffer)));
    std::wstring id = prefix;
    for (wchar_t ch : std::wstring(buffer)) if (iswalnum(ch)) id.push_back(ch);
    return id;
}

void PostUiEvent(UiEventKind kind, const std::wstring& turnId = L"",
                 const std::wstring& text = L"", const std::wstring& detail = L"") {
    if (!g_hwndMain) return;
    auto* event = new UiEvent{kind, turnId, text, detail, {}, {}};
    if (!PostMessageW(g_hwndMain, WM_APP_AI_RESULT, 0, reinterpret_cast<LPARAM>(event))) delete event;
}

bool TransferAll(bool write, void* data, DWORD length) {
    auto* bytes = static_cast<unsigned char*>(data);
    HANDLE event = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!event) return false;
    bool success = true;
    DWORD offset = 0;
    while (offset < length && !g_shutdown.load()) {
        ResetEvent(event);
        OVERLAPPED operation = {};
        operation.hEvent = event;
        DWORD transferred = 0;
        const BOOL completed = write
            ? WriteFile(g_pipe, bytes + offset, length - offset, &transferred, &operation)
            : ReadFile(g_pipe, bytes + offset, length - offset, &transferred, &operation);
        if (!completed) {
            if (GetLastError() != ERROR_IO_PENDING ||
                WaitForSingleObject(event, INFINITE) != WAIT_OBJECT_0 ||
                !GetOverlappedResult(g_pipe, &operation, &transferred, FALSE)) {
                success = false;
                break;
            }
        }
        if (transferred == 0) {
            success = false;
            break;
        }
        offset += transferred;
    }
    CloseHandle(event);
    return success && offset == length;
}

bool WriteAll(const void* data, DWORD length) {
    return TransferAll(true, const_cast<void*>(data), length);
}

bool ReadAll(void* data, DWORD length) {
    return TransferAll(false, data, length);
}

bool SendJson(const Json& message) {
    const std::string payload = message.Serialize();
    if (payload.empty() || payload.size() > kMaxFrameSize) return false;
    {
        std::lock_guard<std::mutex> lock(g_outboundMutex);
        if (g_pipe == INVALID_HANDLE_VALUE || g_shutdown.load()) return false;
        g_outboundFrames.push_back(payload);
        dbg("Agent frame queued bytes=%zu depth=%zu", payload.size(), g_outboundFrames.size());
    }
    g_outboundReady.notify_one();
    return true;
}

void WriterLoop() {
    dbg("Agent writer started");
    while (!g_shutdown.load()) {
        std::string payload;
        {
            std::unique_lock<std::mutex> lock(g_outboundMutex);
            g_outboundReady.wait(lock, []() { return g_shutdown.load() || !g_outboundFrames.empty(); });
            if (g_shutdown.load()) return;
            payload = std::move(g_outboundFrames.front());
            g_outboundFrames.pop_front();
        }
        const DWORD length = static_cast<DWORD>(payload.size());
        dbg("Agent writer sending bytes=%lu", length);
        if (!WriteAll(&length, sizeof(length)) || !WriteAll(payload.data(), length)) {
            g_ready.store(false);
            dbg("Agent writer failed error=%lu", GetLastError());
            return;
        }
        dbg("Agent writer sent bytes=%lu", length);
    }
    dbg("Agent writer stopped");
}

bool SendNotification(const char* method, const Json& parameters) {
    Json message = Json::Object();
    message["jsonrpc"] = Json("2.0");
    message["method"] = Json(method);
    message["params"] = parameters;
    return SendJson(message);
}

bool SendRequest(const char* id, const char* method, const Json& parameters) {
    Json message = Json::Object();
    message["jsonrpc"] = Json("2.0");
    message["id"] = Json(id);
    message["method"] = Json(method);
    message["params"] = parameters;
    return SendJson(message);
}

bool SendResponse(const std::string& id, const Json& result, const std::wstring& error) {
    Json response = Json::Object();
    response["jsonrpc"] = Json("2.0");
    response["id"] = Json(id);
    if (error.empty()) response["result"] = result;
    else {
        Json value = Json::Object();
        value["code"] = Json(-32000);
        value["message"] = Json(WideToUtf8(error));
        response["error"] = value;
    }
    return SendJson(response);
}

Json BuildInitializeRequest() {
    Json config = Json::Object();
    config["baseUrl"] = Json(WideToUtf8(g_config.aiBaseUrl));
    config["model"] = Json(WideToUtf8(g_config.aiModel));
    config["apiKey"] = Json(WideToUtf8(g_config.aiApiKey));
    config["maxTokens"] = Json(g_config.aiMaxTokens);
    config["contextWindowTokens"] = Json(g_config.aiContextWindowTokens);
    config["temperature"] = Json(g_config.aiTemperature);

    Json parameters = Json::Object();
    parameters["protocolVersion"] = Json(kProtocolVersion);
    parameters["appVersion"] = Json("2.0.0");
    parameters["locale"] = Json("zh-CN");
    parameters["dataDirectory"] = Json(WideToUtf8(ApplicationDataDirectory()));
    parameters["config"] = config;
    parameters["tools"] = AnalyticsToolDefinitions();

    Json request = Json::Object();
    request["jsonrpc"] = Json("2.0");
    request["id"] = Json("initialize-1");
    request["method"] = Json("initialize");
    request["params"] = parameters;
    return request;
}

std::wstring EventText(const Json& parameters, const char* key) {
    return Utf8ToWide(parameters.get(key).string());
}

void HandleIncomingRequest(const Json& message) {
    const std::string method = message.get("method").string();
    const Json& parameters = message.get("params");
    if (method == "tool.execute") {
        AnalyticsContext context;
        {
            std::lock_guard<std::mutex> lock(g_contextMutex);
            context = g_analyticsContext;
        }
        std::wstring error;
        Json result = ExecuteAnalyticsTool(parameters.get("name").string(), parameters.get("arguments"), context, error);
        SendResponse(message.get("id").string(), result, error);
        return;
    }

    const std::wstring turnId = EventText(parameters, "turnId");
    if (method == "chat.started") return;
    if (method == "chat.delta") PostUiEvent(UiEventKind::Delta, turnId, EventText(parameters, "text"));
    else if (method == "chat.completed") PostUiEvent(UiEventKind::Completed, turnId, EventText(parameters, "text"));
    else if (method == "chat.failed") PostUiEvent(UiEventKind::Failed, turnId, EventText(parameters, "message"), EventText(parameters, "code"));
    else if (method == "chat.tool_started") PostUiEvent(UiEventKind::ToolStarted, turnId, EventText(parameters, "name"));
    else if (method == "chat.tool_completed") PostUiEvent(UiEventKind::ToolCompleted, turnId, EventText(parameters, "name"));
}

void PostHistory(const std::wstring& sessionId, const Json& values) {
    auto* event = new UiEvent();
    event->kind = UiEventKind::History;
    event->detail = sessionId;
    for (const Json& item : values.array()) {
        ChatMessage chat;
        chat.kind = item.get("role").string() == "user" ? ChatMessage::User : ChatMessage::Assistant;
        chat.text = Utf8ToWide(item.get("content").string());
        event->history.push_back(std::move(chat));
    }
    if (!PostMessageW(g_hwndMain, WM_APP_AI_RESULT, 0, reinterpret_cast<LPARAM>(event))) delete event;
}

void PostSessionList(const Json& values) {
    auto* event = new UiEvent();
    event->kind = UiEventKind::SessionList;
    for (const Json& item : values.array()) {
        ConversationSummary summary;
        summary.id = Utf8ToWide(item.get("id").string());
        summary.title = Utf8ToWide(item.get("title").string());
        if (!summary.id.empty()) event->sessions.push_back(std::move(summary));
    }
    if (!PostMessageW(g_hwndMain, WM_APP_AI_RESULT, 0, reinterpret_cast<LPARAM>(event))) delete event;
}

bool RequestHistory(const std::wstring& sessionId) {
    Json parameters = Json::Object();
    parameters["sessionId"] = Json(WideToUtf8(sessionId));
    return SendRequest(("history|" + WideToUtf8(sessionId)).c_str(), "session.history", parameters);
}

bool RequestSessionList() {
    return SendRequest("session-list", "session.list", Json::Object());
}

void AddWelcomeMessage() {
    ChatMessage welcome;
    welcome.kind = ChatMessage::System;
    welcome.text = g_agentSession.currentDayDate.empty()
        ? L"AI助手已就绪，可以分析当前选中的Git数据。"
        : L"AI助手已就绪，当前日期：" + g_agentSession.currentDayDate;
    g_agentSession.history.push_back(std::move(welcome));
}

void PipeLoop() {
    OVERLAPPED connection = {};
    connection.hEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    BOOL connected = FALSE;
    DWORD connectError = connection.hEvent ? ERROR_SUCCESS : GetLastError();
    if (connection.hEvent) {
        connected = ConnectNamedPipe(g_pipe, &connection);
        if (!connected) {
            connectError = GetLastError();
            if (connectError == ERROR_PIPE_CONNECTED) connected = TRUE;
            else if (connectError == ERROR_IO_PENDING) {
                const DWORD waitResult = WaitForSingleObject(connection.hEvent, 6000);
                if (waitResult == WAIT_OBJECT_0) {
                    DWORD transferred = 0;
                    connected = GetOverlappedResult(g_pipe, &connection, &transferred, FALSE);
                    if (!connected) connectError = GetLastError();
                } else {
                    CancelIoEx(g_pipe, &connection);
                    DWORD transferred = 0;
                    GetOverlappedResult(g_pipe, &connection, &transferred, TRUE);
                    connectError = waitResult == WAIT_TIMEOUT ? ERROR_SEM_TIMEOUT : GetLastError();
                }
            }
        }
        CloseHandle(connection.hEvent);
    }
    dbg("Agent pipe connected=%d error=%lu", connected, connected ? 0UL : connectError);
    if (!connected || g_shutdown.load()) {
        PostUiEvent(UiEventKind::Disconnected, L"", L"AI服务连接失败");
        return;
    }
    SendJson(BuildInitializeRequest());
    while (!g_shutdown.load()) {
        DWORD length = 0;
        if (!ReadAll(&length, sizeof(length))) break;
        if (length == 0 || length > kMaxFrameSize) break;
        std::string payload(length, '\0');
        if (!ReadAll(payload.data(), length)) break;
        Json message;
        std::string parseError;
        if (!Json::Parse(payload, message, parseError)) continue;
        if (message.get("method").isString()) HandleIncomingRequest(message);
        else if (message.get("id").string() == "initialize-1" && message.get("error").isNull()) {
            g_ready.store(true);
            dbg("Agent ready");
            PostUiEvent(UiEventKind::Ready);
            RequestHistory(L"main");
            RequestSessionList();
        } else if (message.get("id").string() == "initialize-1") {
            PostUiEvent(UiEventKind::Failed, L"", L"AI服务初始化失败");
        } else if (message.get("id").string().rfind("history|", 0) == 0 &&
                   message.get("result").get("messages").isArray()) {
            dbg("Agent history loaded: %zu messages", message.get("result").get("messages").array().size());
            PostHistory(Utf8ToWide(message.get("id").string().substr(8)), message.get("result").get("messages"));
        } else if (message.get("id").string() == "session-list" &&
                   message.get("result").get("sessions").isArray()) {
            PostSessionList(message.get("result").get("sessions"));
        } else if (message.get("id").string().rfind("delete|", 0) == 0) {
            RequestSessionList();
        }
    }
    g_ready.store(false);
    if (!g_shutdown.load()) PostUiEvent(UiEventKind::Disconnected, L"", L"AI服务已断开");
}

bool LaunchSidecar(const std::wstring& pipeName) {
    const std::wstring executable = JoinPath(ExeDirectory(), L"agent\\GitLocal.Agent.exe");
    if (!PathExists(executable)) {
        dbg("Sidecar not found: %S", executable.c_str());
        return false;
    }
    std::wstring command = L"\"" + executable + L"\" --pipe \"" + pipeName +
                           L"\" --data-dir \"" + ApplicationDataDirectory() + L"\"";
    const std::wstring logDirectory = JoinPath(ApplicationDataDirectory(), L"logs");
    EnsureDirectory(logDirectory);
    SECURITY_ATTRIBUTES inherited = {sizeof(inherited), nullptr, TRUE};
    HANDLE logFile = CreateFileW(JoinPath(logDirectory, L"agent-sidecar.log").c_str(), FILE_APPEND_DATA,
        FILE_SHARE_READ | FILE_SHARE_WRITE, &inherited, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    HANDLE nullInput = CreateFileW(L"NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
        &inherited, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    STARTUPINFOW startup = {};
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    startup.wShowWindow = SW_HIDE;
    startup.hStdInput = nullInput;
    startup.hStdOutput = logFile;
    startup.hStdError = logFile;
    PROCESS_INFORMATION process = {};
    const BOOL created = CreateProcessW(executable.c_str(), command.data(), nullptr, nullptr, TRUE, CREATE_NO_WINDOW,
                                        nullptr, ExeDirectory().c_str(), &startup, &process);
    if (logFile != INVALID_HANDLE_VALUE) CloseHandle(logFile);
    if (nullInput != INVALID_HANDLE_VALUE) CloseHandle(nullInput);
    if (!created) return false;
    CloseHandle(process.hThread);
    g_process = process.hProcess;
    g_job = CreateJobObjectW(nullptr, nullptr);
    if (g_job) {
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits = {};
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(g_job, JobObjectExtendedLimitInformation, &limits, sizeof(limits));
        AssignProcessToJobObject(g_job, g_process);
    }
    g_processRunning.store(true);
    return true;
}

LRESULT CALLBACK AgentInputSubclass(HWND window, UINT message, WPARAM wParam, LPARAM lParam,
                                    UINT_PTR, DWORD_PTR) {
    if (message == WM_KEYDOWN && wParam == VK_RETURN && !(GetKeyState(VK_SHIFT) & 0x8000)) {
        const int length = GetWindowTextLengthW(window);
        std::wstring text(static_cast<size_t>(length + 1), L'\0');
        if (length > 0) GetWindowTextW(window, text.data(), length + 1);
        text.resize(static_cast<size_t>(length));
        while (!text.empty() && (text.back() == L'\r' || text.back() == L'\n')) text.pop_back();
        if (AgentSend(text)) SetWindowTextW(window, L"");
        return 0;
    }
    if (message == WM_NCDESTROY) RemoveWindowSubclass(window, AgentInputSubclass, 1);
    return DefSubclassProc(window, message, wParam, lParam);
}

AnalyticsContext CaptureContext(const std::wstring& dayDate) {
    AnalyticsContext context;
    context.activeYear = g_year;
    context.selectedDate = dayDate;
    context.repositories = g_repos;
    context.selected = g_selected;
    return context;
}

RECT HeaderButtonRect(const RECT& panelRect, int slot, int width) {
    const double scale = std::max(0.75, std::min(1.5, g_fontScale));
    const int inset = static_cast<int>(std::lround(6.0 * scale));
    const int closeWidth = static_cast<int>(std::lround(30.0 * scale));
    const int gap = static_cast<int>(std::lround(6.0 * scale));
    int right = panelRect.right - closeWidth - inset;
    if (slot >= 1) right -= static_cast<int>(std::lround(66.0 * scale)) + gap;
    if (slot >= 2) right -= static_cast<int>(std::lround(66.0 * scale)) + gap;
    const int scaledWidth = static_cast<int>(std::lround(static_cast<double>(width) * scale));
    return {right - scaledWidth, panelRect.top + inset, right,
            panelRect.top + static_cast<int>(std::lround(static_cast<double>(kAgentHeaderHeight) * scale)) - inset};
}

} // namespace

AgentSession g_agentSession;
std::atomic<bool> g_agentBusy{false};
HWND g_hwndAgentInput = nullptr;

void dbg(const char* format, ...) {
    char buffer[2048] = {};
    va_list arguments;
    va_start(arguments, format);
    vsnprintf_s(buffer, sizeof(buffer), _TRUNCATE, format, arguments);
    va_end(arguments);
    OutputDebugStringA(buffer);
    OutputDebugStringA("\n");
    const std::wstring logDirectory = JoinPath(ApplicationDataDirectory(), L"logs");
    EnsureDirectory(logDirectory);
    FILE* file = nullptr;
    if (_wfopen_s(&file, JoinPath(logDirectory, L"app.log").c_str(), L"a") == 0 && file) {
        std::fprintf(file, "[%lu] %s\n", GetTickCount(), buffer);
        std::fclose(file);
    }
}

bool AgentStartProcess() {
    if (g_processRunning.load()) return true;
    g_shutdown.store(false);
    const std::wstring pipeName = NewId(L"GitLocal.Agent.");
    const std::wstring fullPipeName = L"\\\\.\\pipe\\" + pipeName;
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    SECURITY_ATTRIBUTES security = {sizeof(security), nullptr, FALSE};
    if (ConvertStringSecurityDescriptorToSecurityDescriptorW(L"D:P(A;;GA;;;OW)", SDDL_REVISION_1, &descriptor, nullptr))
        security.lpSecurityDescriptor = descriptor;
    g_pipe = CreateNamedPipeW(fullPipeName.c_str(), PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
        PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
        1, kMaxFrameSize, kMaxFrameSize, 5000, security.lpSecurityDescriptor ? &security : nullptr);
    if (descriptor) LocalFree(descriptor);
    if (g_pipe == INVALID_HANDLE_VALUE) return false;
    if (!LaunchSidecar(pipeName)) {
        CloseHandle(g_pipe);
        g_pipe = INVALID_HANDLE_VALUE;
        return false;
    }
    {
        std::lock_guard<std::mutex> lock(g_outboundMutex);
        g_outboundFrames.clear();
    }
    g_writerThread = std::thread(WriterLoop);
    g_pipeThread = std::thread(PipeLoop);
    return true;
}

void AgentStopProcess() {
    dbg("Agent shutdown begin");
    g_ready.store(false);
    g_shutdown.store(true);
    g_outboundReady.notify_all();
    if (g_pipe != INVALID_HANDLE_VALUE) {
        CancelIoEx(g_pipe, nullptr);
        if (g_pipeThread.joinable()) CancelSynchronousIo(g_pipeThread.native_handle());
        if (g_writerThread.joinable()) CancelSynchronousIo(g_writerThread.native_handle());
    }
    if (g_pipeThread.joinable()) g_pipeThread.join();
    if (g_writerThread.joinable()) g_writerThread.join();
    if (g_pipe != INVALID_HANDLE_VALUE) {
        DisconnectNamedPipe(g_pipe);
        CloseHandle(g_pipe);
        g_pipe = INVALID_HANDLE_VALUE;
    }
    if (g_process) {
        const DWORD waitResult = WaitForSingleObject(g_process, 5000);
        DWORD exitCode = STILL_ACTIVE;
        GetExitCodeProcess(g_process, &exitCode);
        if (waitResult == WAIT_TIMEOUT && g_job) {
            TerminateJobObject(g_job, 1);
            WaitForSingleObject(g_process, 2000);
            GetExitCodeProcess(g_process, &exitCode);
        }
        dbg("Agent shutdown wait=%lu exit=0x%08lx", waitResult, exitCode);
        CloseHandle(g_process);
        g_process = nullptr;
    }
    if (g_job) { CloseHandle(g_job); g_job = nullptr; }
    g_processRunning.store(false);
    dbg("Agent shutdown complete");
}

bool AgentProcessIsRunning() {
    if (!g_processRunning.load() || !g_process) return false;
    DWORD exitCode = 0;
    return GetExitCodeProcess(g_process, &exitCode) && exitCode == STILL_ACTIVE;
}

bool AgentIsReady() { return g_ready.load(); }

void AgentStart(const std::wstring& dayDate) {
    if (!g_config.aiPrivacyConsent) {
        const int choice = MessageBoxW(g_hwndMain,
            L"AI分析会将完成当前请求所需的脱敏Git提交信息发送到云端模型。默认不会发送仓库绝对路径。是否继续？",
            L"启用AI助手", MB_ICONINFORMATION | MB_YESNO | MB_DEFBUTTON2);
        if (choice != IDYES) return;
        g_config.aiPrivacyConsent = true;
        std::wstring error;
        SaveAppConfig(g_config, error);
    }
    g_agentSession.active = true;
    g_agentSession.currentDayDate = dayDate;
    g_agentSession.lastError.clear();
    if (g_agentSession.history.empty()) AddWelcomeMessage();
    if (!g_hwndAgentInput) {
        g_hwndAgentInput = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
            WS_CHILD | WS_VISIBLE | ES_MULTILINE | ES_AUTOVSCROLL | WS_VSCROLL,
            0, 0, 0, 0, g_hwndMain, reinterpret_cast<HMENU>(IDC_AGENT_INPUT), GetModuleHandleW(nullptr), nullptr);
        AgentRefreshInputFont();
        SendMessageW(g_hwndAgentInput, EM_SETCUEBANNER, TRUE,
                     reinterpret_cast<LPARAM>(L"输入问题，Enter发送，Shift+Enter换行"));
        SetWindowSubclass(g_hwndAgentInput, AgentInputSubclass, 1, 0);
    }
    SetFocus(g_hwndAgentInput);
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void AgentStop() {
    g_agentSession.active = false;
    if (g_hwndAgentInput) { DestroyWindow(g_hwndAgentInput); g_hwndAgentInput = nullptr; }
    if (g_agentFont) { DeleteObject(g_agentFont); g_agentFont = nullptr; }
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}


bool AgentSend(const std::wstring& message) {
    if (!g_agentSession.active || message.empty() || g_agentBusy.load()) return false;
    if (!AgentIsReady()) {
        g_agentSession.lastError = L"AI服务尚未就绪，请稍后重试";
        InvalidateRect(g_hwndMain, nullptr, FALSE);
        return false;
    }
    g_agentSession.currentTurnId = NewId(L"turn-");
    g_agentSession.history.push_back({ChatMessage::User, message});
    g_agentSession.history.push_back({ChatMessage::Assistant, L""});
    while (g_agentSession.history.size() > 80) g_agentSession.history.pop_front();
    ++g_agentSession.contentRevision;
    {
        std::lock_guard<std::mutex> lock(g_contextMutex);
        g_analyticsContext = CaptureContext(g_agentSession.currentDayDate);
    }
    Json context = Json::Object();
    context["activeYear"] = Json(g_year);
    context["selectedDate"] = Json(WideToUtf8(g_agentSession.currentDayDate));
    context["selectedRepositoryCount"] = Json(SelectedRepositoryCount());
    Json selectedRepositories = Json::Array();
    for (const Repository& repository : g_repos) {
        const auto selected = g_selected.find(repository.id);
        if (repository.available && selected != g_selected.end() && selected->second)
            selectedRepositories.push(Json(WideToUtf8(repository.name)));
    }
    context["selectedRepositories"] = selectedRepositories;
    context["projectScope"] = Json("git-local-default");
    Json parameters = Json::Object();
    parameters["sessionId"] = Json(WideToUtf8(g_agentSession.id));
    parameters["turnId"] = Json(WideToUtf8(g_agentSession.currentTurnId));
    parameters["message"] = Json(WideToUtf8(message));
    parameters["context"] = context;
    g_agentBusy.store(true);
    g_agentSession.lastError.clear();
    if (!SendNotification("chat.start", parameters)) {
        g_agentBusy.store(false);
        g_agentSession.lastError = L"无法发送AI请求";
        return false;
    }
    InvalidateRect(g_hwndMain, nullptr, FALSE);
    return true;
}

void AgentCancel() {
    if (!g_agentBusy.load()) return;
    Json parameters = Json::Object();
    parameters["turnId"] = Json(WideToUtf8(g_agentSession.currentTurnId));
    SendNotification("chat.cancel", parameters);
}

bool AgentIsBusy() { return g_agentBusy.load(); }

void AgentHandleResult(LPARAM eventPointer) {
    std::unique_ptr<UiEvent> event(reinterpret_cast<UiEvent*>(eventPointer));
    if (!event) return;
    if (!event->turnId.empty() && event->turnId != g_agentSession.currentTurnId) return;
    switch (event->kind) {
    case UiEventKind::Ready:
        g_agentSession.lastError.clear();
        break;
    case UiEventKind::History:
        if (event->detail == g_agentSession.id && !g_agentBusy.load() &&
            (g_agentSession.history.empty() ||
             (g_agentSession.history.size() == 1 && g_agentSession.history.front().kind == ChatMessage::System))) {
            g_agentSession.history = std::move(event->history);
            if (g_agentSession.history.empty()) AddWelcomeMessage();
            ++g_agentSession.contentRevision;
        }
        break;
    case UiEventKind::SessionList:
        g_conversations = std::move(event->sessions);
        break;
    case UiEventKind::Delta:
        if (!g_agentSession.history.empty() && g_agentSession.history.back().kind == ChatMessage::Assistant)
            g_agentSession.history.back().text += event->text;
        ++g_agentSession.contentRevision;
        break;
    case UiEventKind::ToolStarted:
        g_agentSession.history.push_back({ChatMessage::Tool, L"正在调用工具：" + event->text + L" ..."});
        ++g_agentSession.contentRevision;
        break;
    case UiEventKind::ToolCompleted:
        if (!g_agentSession.history.empty() && g_agentSession.history.back().kind == ChatMessage::Tool)
            g_agentSession.history.back().text = L"已完成工具调用：" + event->text;
        ++g_agentSession.contentRevision;
        break;
    case UiEventKind::Completed:
        if (!event->text.empty()) {
            if (!g_agentSession.history.empty() && g_agentSession.history.back().kind == ChatMessage::Assistant &&
                g_agentSession.history.back().text.empty())
                g_agentSession.history.back().text += event->text;
            else if (g_agentSession.history.empty() || g_agentSession.history.back().kind != ChatMessage::Assistant)
                g_agentSession.history.push_back({ChatMessage::Assistant, event->text});
            ++g_agentSession.contentRevision;
        }
        g_agentBusy.store(false);
        RequestSessionList();
        break;
    case UiEventKind::Failed:
    case UiEventKind::Disconnected:
        g_agentBusy.store(false);
        g_agentSession.lastError = event->text;
        g_agentSession.history.push_back({ChatMessage::System, L"请求失败：" + event->text});
        ++g_agentSession.contentRevision;
        break;
    }
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void AgentNewConversation() {
    if (g_agentBusy.load()) return;
    g_agentSession.id = NewId(L"session-");
    g_agentSession.currentTurnId.clear();
    g_agentSession.lastError.clear();
    g_agentSession.history.clear();
    AddWelcomeMessage();
    ++g_agentSession.contentRevision;
    if (g_hwndAgentInput) SetWindowTextW(g_hwndAgentInput, L"");
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void AgentClearConversation() {
    if (g_agentBusy.load()) return;
    if (MessageBoxW(g_hwndMain, L"确定清空当前对话吗？此操作会删除本会话的全部消息。",
                    L"清空对话", MB_ICONWARNING | MB_OKCANCEL | MB_DEFBUTTON2) != IDOK) return;
    if (!AgentIsReady()) {
        MessageBoxW(g_hwndMain, L"AI服务尚未就绪，暂时无法清空持久化对话。", L"清空对话", MB_ICONERROR | MB_OK);
        return;
    }
    Json parameters = Json::Object();
    parameters["sessionId"] = Json(WideToUtf8(g_agentSession.id));
    SendRequest(("delete|" + WideToUtf8(g_agentSession.id)).c_str(), "session.delete", parameters);
    g_agentSession.currentTurnId.clear();
    g_agentSession.lastError.clear();
    g_agentSession.history.clear();
    AddWelcomeMessage();
    ++g_agentSession.contentRevision;
    if (g_hwndAgentInput) SetWindowTextW(g_hwndAgentInput, L"");
    InvalidateRect(g_hwndMain, nullptr, FALSE);
}

void AgentShowConversationMenu(HWND owner, int screenX, int screenY) {
    if (g_agentBusy.load()) return;
    RequestSessionList();
    HMENU menu = CreatePopupMenu();
    if (!menu) return;
    constexpr UINT kFirstConversationCommand = 41000;
    if (g_conversations.empty()) {
        AppendMenuW(menu, MF_STRING | MF_GRAYED, kFirstConversationCommand, L"暂无历史对话");
    } else {
        const size_t count = std::min<size_t>(g_conversations.size(), 50);
        for (size_t index = 0; index < count; ++index) {
            std::wstring title = g_conversations[index].title.empty() ? L"未命名对话" : g_conversations[index].title;
            if (title.size() > 36) title.resize(36);
            const UINT flags = MF_STRING | (g_conversations[index].id == g_agentSession.id ? MF_CHECKED : 0);
            AppendMenuW(menu, flags, kFirstConversationCommand + static_cast<UINT>(index), title.c_str());
        }
    }
    const UINT command = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_LEFTALIGN | TPM_TOPALIGN,
                                        screenX, screenY, 0, owner, nullptr);
    if (command >= kFirstConversationCommand &&
        command < kFirstConversationCommand + g_conversations.size()) {
        const auto& selected = g_conversations[command - kFirstConversationCommand];
        if (selected.id != g_agentSession.id) {
            g_agentSession.id = selected.id;
            g_agentSession.currentTurnId.clear();
            g_agentSession.lastError.clear();
            g_agentSession.history.clear();
            AddWelcomeMessage();
            ++g_agentSession.contentRevision;
            RequestHistory(g_agentSession.id);
            if (g_hwndAgentInput) SetWindowTextW(g_hwndAgentInput, L"");
            InvalidateRect(g_hwndMain, nullptr, FALSE);
        }
    }
    DestroyMenu(menu);
}

void AgentRefreshInputFont() {
    if (!g_hwndAgentInput) return;
    const int pixels = std::max(14, static_cast<int>(std::lround(15.0 * g_fontScale)));
    HFONT font = CreateFontW(-pixels, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_DONTCARE, FONT_FAMILY);
    if (!font) return;
    SendMessageW(g_hwndAgentInput, WM_SETFONT, reinterpret_cast<WPARAM>(font), TRUE);
    if (g_agentFont) DeleteObject(g_agentFont);
    g_agentFont = font;
}

bool AgentInputHitTest(int x, int y, const RECT& panelRect) {
    const double scale = std::max(0.75, std::min(1.5, g_fontScale));
    const int inset = static_cast<int>(std::lround(static_cast<double>(kAgentInset) * scale));
    const int inputHeight = static_cast<int>(std::lround(static_cast<double>(kAgentInputHeight) * scale));
    RECT input = {panelRect.left + inset, panelRect.bottom - inputHeight - inset,
                  panelRect.right - static_cast<int>(std::lround(92.0 * scale)), panelRect.bottom - inset};
    return PtInRect(&input, POINT{x, y}) != FALSE;
}

bool AgentConversationBtnHitTest(int x, int y, const RECT& panelRect) {
    const RECT button = HeaderButtonRect(panelRect, 2, 66);
    return PtInRect(&button, POINT{x, y}) != FALSE;
}

bool AgentNewBtnHitTest(int x, int y, const RECT& panelRect) {
    const RECT button = HeaderButtonRect(panelRect, 1, 66);
    return PtInRect(&button, POINT{x, y}) != FALSE;
}

bool AgentClearBtnHitTest(int x, int y, const RECT& panelRect) {
    const RECT button = HeaderButtonRect(panelRect, 0, 66);
    return PtInRect(&button, POINT{x, y}) != FALSE;
}

bool AgentSendBtnHitTest(int x, int y, const RECT& panelRect) {
    const double scale = std::max(0.75, std::min(1.5, g_fontScale));
    const int inset = static_cast<int>(std::lround(12.0 * scale));
    const int inputHeight = static_cast<int>(std::lround(static_cast<double>(kAgentInputHeight) * scale));
    const int buttonHeight = static_cast<int>(std::lround(52.0 * scale));
    const int inputTop = panelRect.bottom - inputHeight - inset;
    const int buttonTop = inputTop + (inputHeight - buttonHeight) / 2;
    RECT button = {panelRect.right - static_cast<int>(std::lround(84.0 * scale)),
                   buttonTop, panelRect.right - inset, buttonTop + buttonHeight};
    return PtInRect(&button, POINT{x, y}) != FALSE;
}


void AgentInputChanged(HWND) {}
