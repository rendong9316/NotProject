#include "git_scan.h"

#include "platform.h"

#include <windows.h>
#include <bcrypt.h>
#include <algorithm>
#include <condition_variable>
#include <cstdint>
#include <cwctype>
#include <deque>
#include <mutex>
#include <set>
#include <sstream>
#include <thread>

namespace {

std::wstring QuoteArgument(const std::wstring& argument) {
    if (argument.find_first_of(L" \t\n\v\"") == std::wstring::npos) return argument;
    std::wstring output = L"\"";
    unsigned int slashes = 0;
    for (wchar_t character : argument) {
        if (character == L'\\') {
            ++slashes;
        } else if (character == L'"') {
            output.append(slashes * 2 + 1, L'\\');
            output.push_back(L'"');
            slashes = 0;
        } else {
            output.append(slashes, L'\\');
            slashes = 0;
            output.push_back(character);
        }
    }
    output.append(slashes * 2, L'\\');
    output.push_back(L'"');
    return output;
}

std::wstring HashText(const std::string& value) {
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    DWORD objectSize = 0;
    DWORD resultSize = 0;
    std::vector<unsigned char> object;
    unsigned char digest[32] = {};
    if (BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, 0) < 0 ||
        BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PUCHAR>(&objectSize),
                          sizeof(objectSize), &resultSize, 0) < 0) {
        if (algorithm) BCryptCloseAlgorithmProvider(algorithm, 0);
        return L"";
    }
    object.resize(objectSize);
    const bool success = BCryptCreateHash(algorithm, &hash, object.data(), objectSize, nullptr, 0, 0) >= 0 &&
        BCryptHashData(hash, reinterpret_cast<PUCHAR>(const_cast<char*>(value.data())),
                       static_cast<ULONG>(value.size()), 0) >= 0 &&
        BCryptFinishHash(hash, digest, sizeof(digest), 0) >= 0;
    if (hash) BCryptDestroyHash(hash);
    BCryptCloseAlgorithmProvider(algorithm, 0);
    if (!success) return L"";
    static const wchar_t hex[] = L"0123456789abcdef";
    std::wstring output;
    output.reserve(64);
    for (unsigned char byte : digest) {
        output.push_back(hex[byte >> 4]);
        output.push_back(hex[byte & 0x0f]);
    }
    return output;
}

std::string Base64Url(const std::string& value) {
    static const char alphabet[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    std::string output;
    unsigned int accumulator = 0;
    int bits = -6;
    for (unsigned char byte : value) {
        accumulator = (accumulator << 8) | byte;
        bits += 8;
        while (bits >= 0) {
            output.push_back(alphabet[(accumulator >> bits) & 63]);
            bits -= 6;
        }
    }
    if (bits > -6) output.push_back(alphabet[((accumulator << 8) >> (bits + 8)) & 63]);
    return output;
}

bool IsIgnored(const std::wstring& name) {
    static const wchar_t* ignored[] = {
        L"$recycle.bin", L"system volume information", L"node_modules", L".next", L".nuxt",
        L".cache", L"dist", L"build", L"coverage", L"vendor", L"extern", L"third_party",
        L"_deps", L".ezvcpkg", L".venv", L"venv", L"windowsapps", L"appdata", nullptr
    };
    const std::wstring lower = Lowercase(name);
    for (int index = 0; ignored[index]; ++index) if (lower == ignored[index]) return true;
    return false;
}

bool IsRepository(const std::wstring& directory) {
    return PathExists(JoinPath(directory, L".git"));
}

std::wstring NormalizePath(const std::wstring& path) {
    wchar_t buffer[32768] = {};
    const DWORD length = GetFullPathNameW(path.c_str(), _countof(buffer), buffer, nullptr);
    std::wstring output = length && length < _countof(buffer) ? std::wstring(buffer, length) : path;
    while (output.size() > 3 && (output.back() == L'\\' || output.back() == L'/')) output.pop_back();
    return output;
}

} // namespace

bool GitScan::RunGit(const std::vector<std::wstring>& arguments, std::string& output,
                     std::wstring& error, unsigned long timeoutMs) {
    SECURITY_ATTRIBUTES security = { sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE };
    HANDLE readPipe = nullptr;
    HANDLE writePipe = nullptr;
    if (!CreatePipe(&readPipe, &writePipe, &security, 0)) {
        error = L"创建 Git 输出管道失败：" + WindowsErrorMessage(GetLastError());
        return false;
    }
    SetHandleInformation(readPipe, HANDLE_FLAG_INHERIT, 0);

    std::wstring command = L"git.exe";
    for (const std::wstring& argument : arguments) command += L" " + QuoteArgument(argument);
    std::vector<wchar_t> mutableCommand(command.begin(), command.end());
    mutableCommand.push_back(L'\0');

    STARTUPINFOW startup = {};
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    startup.wShowWindow = SW_HIDE;
    startup.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    startup.hStdOutput = writePipe;
    startup.hStdError = writePipe;
    PROCESS_INFORMATION process = {};
    const BOOL started = CreateProcessW(nullptr, mutableCommand.data(), nullptr, nullptr, TRUE,
                                        CREATE_NO_WINDOW, nullptr, nullptr, &startup, &process);
    CloseHandle(writePipe);
    if (!started) {
        const DWORD code = GetLastError();
        CloseHandle(readPipe);
        error = code == ERROR_FILE_NOT_FOUND
            ? L"找不到 git.exe，请先安装 Git for Windows 并加入 PATH。"
            : L"无法启动 Git：" + WindowsErrorMessage(code);
        return false;
    }

    output.clear();
    char buffer[8192];
    const unsigned long long deadline = GetTickCount64() + timeoutMs;
    bool timedOut = false;
    while (true) {
        DWORD available = 0;
        if (!PeekNamedPipe(readPipe, nullptr, 0, nullptr, &available, nullptr)) break;
        if (available) {
            DWORD read = 0;
            if (!ReadFile(readPipe, buffer, std::min<DWORD>(available, sizeof(buffer)), &read, nullptr) || !read) break;
            output.append(buffer, read);
            if (output.size() > 128 * 1024 * 1024) {
                TerminateProcess(process.hProcess, ERROR_BUFFER_OVERFLOW);
                error = L"Git 输出超过 128 MB，已终止操作。";
                break;
            }
            continue;
        }
        const DWORD wait = WaitForSingleObject(process.hProcess, 20);
        if (wait == WAIT_OBJECT_0) {
            DWORD read = 0;
            while (ReadFile(readPipe, buffer, sizeof(buffer), &read, nullptr) && read) output.append(buffer, read);
            break;
        }
        if (GetTickCount64() >= deadline) {
            timedOut = true;
            TerminateProcess(process.hProcess, WAIT_TIMEOUT);
            error = L"Git 操作超时。";
            break;
        }
    }
    WaitForSingleObject(process.hProcess, 2000);
    DWORD exitCode = 1;
    GetExitCodeProcess(process.hProcess, &exitCode);
    CloseHandle(readPipe);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    if (timedOut || !error.empty()) return false;
    if (exitCode != 0) {
        std::wstring detail = Utf8ToWide(output.substr(0, 4096));
        while (!detail.empty() && (detail.back() == L'\r' || detail.back() == L'\n')) detail.pop_back();
        error = detail.empty() ? L"Git 命令执行失败，退出码 " + FormatInteger(static_cast<int>(exitCode)) : detail;
        return false;
    }
    return true;
}

std::wstring GitScan::RepositoryId(const std::wstring& path) {
    return Utf8ToWide(Base64Url(WideToUtf8(Lowercase(NormalizePath(path)))));
}

std::wstring GitScan::FilterSignature(const AppConfig& config) {
    std::vector<std::wstring> authors = config.authors;
    for (std::wstring& author : authors) author = Lowercase(author);
    std::sort(authors.begin(), authors.end());
    std::string value = "{\"authors\":[";
    for (size_t index = 0; index < authors.size(); ++index) {
        if (index) value += ',';
        value += '"' + WideToUtf8(authors[index]) + '"';
    }
    value += "],\"includeAllAuthors\":";
    value += config.includeAllAuthors ? "true}" : "false}";
    return HashText(value);
}

bool GitScan::ReadFingerprint(const std::wstring& path, std::wstring& fingerprint, std::wstring& error) {
    std::string output;
    if (!RunGit({L"--no-pager", L"-C", path, L"for-each-ref", L"--sort=refname",
                 L"--format=%(refname)%00%(objectname)%00%(*objectname)"}, output, error)) return false;
    fingerprint = HashText(output);
    return true;
}

bool GitScan::CollectHistory(const std::wstring& path, const AppConfig& config,
                             std::map<std::wstring, int>& days,
                             std::vector<DayEntry::CommitEntry>& commits,
                             std::wstring& error) {
    std::string output;
    if (!RunGit({L"--no-pager", L"-C", path, L"log", L"--all", L"--date=format:%Y-%m-%d",
                 L"--pretty=format:%ad%x1f%ai%x1f%H%x1f%an%x1f%ae%x1f%s%x1e"},
                output, error, 30 * 60 * 1000)) return false;

    std::set<std::wstring> authors;
    for (const std::wstring& author : config.authors) authors.insert(Lowercase(author));
    const bool includeAll = config.includeAllAuthors || authors.empty();
    days.clear();
    commits.clear();

    size_t position = 0;
    while (position < output.size()) {
        const size_t recordEnd = output.find('\x1e', position);
        std::string record = output.substr(position, recordEnd == std::string::npos
                                                        ? std::string::npos
                                                        : recordEnd - position);
        while (!record.empty() && (record.front() == '\r' || record.front() == '\n')) record.erase(record.begin());
        while (!record.empty() && (record.back() == '\r' || record.back() == '\n')) record.pop_back();

        std::vector<std::string> fields;
        size_t fieldStart = 0;
        for (int field = 0; field < 5; ++field) {
            const size_t separator = record.find('\x1f', fieldStart);
            if (separator == std::string::npos) break;
            fields.push_back(record.substr(fieldStart, separator - fieldStart));
            fieldStart = separator + 1;
        }
        if (fields.size() == 5) {
            fields.push_back(record.substr(fieldStart));
            const std::wstring date = Utf8ToWide(fields[0]);
            const std::wstring isoTime = Utf8ToWide(fields[1]);
            const std::wstring name = Utf8ToWide(fields[3]);
            const std::wstring email = Lowercase(Utf8ToWide(fields[4]));
            if (date.size() == 10 && (includeAll || authors.count(email) || authors.count(Lowercase(name)))) {
                DayEntry::CommitEntry entry;
                entry.date = date;
                entry.hash = Utf8ToWide(fields[2]);
                const size_t space = isoTime.find(L' ');
                if (space != std::wstring::npos && isoTime.size() >= space + 9)
                    entry.time = isoTime.substr(space + 1, 8);
                entry.author = name;
                entry.message = Utf8ToWide(fields[5]);
                commits.push_back(entry);
                ++days[date];
            }
        }
        if (recordEnd == std::string::npos) break;
        position = recordEnd + 1;
    }
    return true;
}

bool GitScan::IsGitAvailable(std::wstring& version, std::wstring& error) {
    std::string output;
    if (!RunGit({L"--version"}, output, error, 10000)) return false;
    while (!output.empty() && (output.back() == '\r' || output.back() == '\n')) output.pop_back();
    version = Utf8ToWide(output);
    return true;
}

std::vector<std::wstring> GitScan::FindRepositories(const std::vector<std::wstring>& roots,
                                                     int maxDepth, int concurrency,
                                                     std::atomic<bool>& cancel,
                                                     const Progress& progress) {
    struct WorkItem { std::wstring path; int depth; };
    std::deque<WorkItem> queue;
    for (const std::wstring& root : roots) if (DirectoryExists(root)) queue.push_back({NormalizePath(root), 0});
    std::mutex mutex;
    std::condition_variable condition;
    std::set<std::wstring> found;
    int active = 0;
    int visited = 0;
    bool finished = queue.empty();

    const auto worker = [&]() {
        while (!cancel.load()) {
            WorkItem item;
            {
                std::unique_lock<std::mutex> lock(mutex);
                condition.wait(lock, [&] { return cancel.load() || finished || !queue.empty(); });
                if (cancel.load() || (finished && queue.empty())) return;
                item = queue.front();
                queue.pop_front();
                ++active;
            }

            bool repository = IsRepository(item.path);
            std::vector<WorkItem> children;
            if (!repository && item.depth < maxDepth) {
                WIN32_FIND_DATAW data = {};
                HANDLE search = FindFirstFileExW(JoinPath(item.path, L"*").c_str(), FindExInfoBasic,
                                                  &data, FindExSearchNameMatch, nullptr,
                                                  FIND_FIRST_EX_LARGE_FETCH);
                if (search != INVALID_HANDLE_VALUE) {
                    do {
                        if ((data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) continue;
                        if ((data.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0) continue;
                        const std::wstring name = data.cFileName;
                        if (name == L"." || name == L".." || IsIgnored(name)) continue;
                        children.push_back({JoinPath(item.path, name), item.depth + 1});
                    } while (!cancel.load() && FindNextFileW(search, &data));
                    FindClose(search);
                }
            }

            int currentVisited = 0;
            int pendingCount = 0;
            {
                std::lock_guard<std::mutex> lock(mutex);
                if (repository) found.insert(item.path);
                for (const WorkItem& child : children) queue.push_back(child);
                --active;
                currentVisited = ++visited;
                pendingCount = static_cast<int>(queue.size()) + active;
                if (queue.empty() && active == 0) finished = true;
            }
            if (progress && (currentVisited % 100 == 0 || finished)) progress(currentVisited, pendingCount);
            condition.notify_all();
        }
    };

    std::vector<std::thread> workers;
    const int count = std::max(1, std::min(32, concurrency));
    for (int index = 0; index < count; ++index) workers.emplace_back(worker);
    condition.notify_all();
    for (std::thread& thread : workers) thread.join();
    return std::vector<std::wstring>(found.begin(), found.end());
}
