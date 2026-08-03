#include "platform.h"

#include <windows.h>
#include <shlobj.h>
#include <algorithm>
#include <cwctype>
#include <sstream>

std::wstring Utf8ToWide(const std::string& value) {
    if (value.empty()) return L"";
    const int length = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                                            static_cast<int>(value.size()), nullptr, 0);
    if (length <= 0) return L"";
    std::wstring output(static_cast<size_t>(length), L'\0');
    MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()),
                        &output[0], length);
    return output;
}

std::string WideToUtf8(const std::wstring& value) {
    if (value.empty()) return "";
    const int length = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(),
                                            static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
    if (length <= 0) return "";
    std::string output(static_cast<size_t>(length), '\0');
    WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()),
                        &output[0], length, nullptr, nullptr);
    return output;
}

std::wstring JoinPath(const std::wstring& left, const std::wstring& right) {
    if (left.empty()) return right;
    if (right.empty()) return left;
    const wchar_t tail = left.back();
    return (tail == L'\\' || tail == L'/') ? left + right : left + L"\\" + right;
}

std::wstring ExeDirectory() {
    std::vector<wchar_t> buffer(32768, L'\0');
    const DWORD length = GetModuleFileNameW(nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
    if (!length || length >= buffer.size()) return L".";
    return ParentPath(std::wstring(buffer.data(), length));
}

std::wstring ParentPath(const std::wstring& path) {
    if (path.empty()) return L"";
    size_t end = path.size();
    while (end > 0 && (path[end - 1] == L'\\' || path[end - 1] == L'/')) --end;
    const size_t slash = path.find_last_of(L"\\/", end == 0 ? 0 : end - 1);
    if (slash == std::wstring::npos) return L"";
    if (slash == 2 && path.size() >= 3 && path[1] == L':') return path.substr(0, 3);
    return path.substr(0, slash);
}

std::wstring BaseName(const std::wstring& path) {
    size_t end = path.size();
    while (end > 0 && (path[end - 1] == L'\\' || path[end - 1] == L'/')) --end;
    const size_t slash = path.find_last_of(L"\\/", end == 0 ? 0 : end - 1);
    return path.substr(slash == std::wstring::npos ? 0 : slash + 1, end - (slash == std::wstring::npos ? 0 : slash + 1));
}

std::wstring Lowercase(const std::wstring& value) {
    std::wstring output(value);
    std::transform(output.begin(), output.end(), output.begin(), towlower);
    return output;
}

std::wstring FormatInteger(int value) {
    wchar_t buffer[64];
    _snwprintf_s(buffer, _countof(buffer), _TRUNCATE, L"%d", value);
    return buffer;
}

std::wstring FormatDuration(unsigned long long milliseconds) {
    wchar_t buffer[64];
    if (milliseconds < 1000) _snwprintf_s(buffer, _countof(buffer), _TRUNCATE, L"%llu 毫秒", milliseconds);
    else _snwprintf_s(buffer, _countof(buffer), _TRUNCATE, L"%.1f 秒", milliseconds / 1000.0);
    return buffer;
}

bool PathExists(const std::wstring& path) { return GetFileAttributesW(path.c_str()) != INVALID_FILE_ATTRIBUTES; }

bool DirectoryExists(const std::wstring& path) {
    const DWORD attributes = GetFileAttributesW(path.c_str());
    return attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
}

DirectoryState ProbeDirectory(const std::wstring& path) {
    const DWORD attributes = GetFileAttributesW(path.c_str());
    if (attributes != INVALID_FILE_ATTRIBUTES)
        return (attributes & FILE_ATTRIBUTE_DIRECTORY) != 0 ? DirectoryState::Exists : DirectoryState::Missing;

    const DWORD error = GetLastError();
    if (error != ERROR_FILE_NOT_FOUND && error != ERROR_PATH_NOT_FOUND && error != ERROR_INVALID_NAME)
        return DirectoryState::Inaccessible;

    std::wstring ancestor = ParentPath(path);
    while (!ancestor.empty()) {
        const DWORD ancestorAttributes = GetFileAttributesW(ancestor.c_str());
        if (ancestorAttributes != INVALID_FILE_ATTRIBUTES) return DirectoryState::Missing;

        const DWORD ancestorError = GetLastError();
        if (ancestorError != ERROR_FILE_NOT_FOUND && ancestorError != ERROR_PATH_NOT_FOUND &&
            ancestorError != ERROR_INVALID_NAME)
            return DirectoryState::Inaccessible;

        const std::wstring parent = ParentPath(ancestor);
        if (parent.empty() || parent == ancestor) break;
        ancestor = parent;
    }
    return DirectoryState::Inaccessible;
}

bool EnsureDirectory(const std::wstring& path) {
    if (DirectoryExists(path)) return true;
    return SHCreateDirectoryExW(nullptr, path.c_str(), nullptr) == ERROR_SUCCESS || DirectoryExists(path);
}

std::wstring WindowsErrorMessage(unsigned long code) {
    wchar_t* raw = nullptr;
    const DWORD length = FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM |
                                         FORMAT_MESSAGE_IGNORE_INSERTS, nullptr, code, 0,
                                         reinterpret_cast<wchar_t*>(&raw), 0, nullptr);
    std::wstring message = length && raw ? std::wstring(raw, length) : L"Windows 错误 " + FormatInteger(static_cast<int>(code));
    if (raw) LocalFree(raw);
    while (!message.empty() && (message.back() == L'\r' || message.back() == L'\n' || message.back() == L' ')) message.pop_back();
    return message;
}

bool ReadUtf8File(const std::wstring& path, std::string& content, std::wstring& error) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                              nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        error = L"无法读取 " + path + L"：" + WindowsErrorMessage(GetLastError());
        return false;
    }
    LARGE_INTEGER size = {};
    if (!GetFileSizeEx(file, &size) || size.QuadPart > 128LL * 1024 * 1024) {
        error = L"文件过大或无法读取：" + path;
        CloseHandle(file);
        return false;
    }
    content.assign(static_cast<size_t>(size.QuadPart), '\0');
    DWORD total = 0;
    while (total < content.size()) {
        DWORD read = 0;
        const DWORD chunk = static_cast<DWORD>(std::min<size_t>(content.size() - total, 1 << 20));
        if (!ReadFile(file, &content[total], chunk, &read, nullptr)) {
            error = L"读取失败：" + WindowsErrorMessage(GetLastError());
            CloseHandle(file);
            return false;
        }
        if (!read) break;
        total += read;
    }
    CloseHandle(file);
    content.resize(total);
    if (content.size() >= 3 && static_cast<unsigned char>(content[0]) == 0xef &&
        static_cast<unsigned char>(content[1]) == 0xbb && static_cast<unsigned char>(content[2]) == 0xbf)
        content.erase(0, 3);
    return true;
}

bool WriteUtf8FileAtomic(const std::wstring& path, const std::string& content, std::wstring& error) {
    if (!EnsureDirectory(ParentPath(path))) {
        error = L"无法创建目录：" + ParentPath(path);
        return false;
    }
    wchar_t suffix[80];
    _snwprintf_s(suffix, _countof(suffix), _TRUNCATE, L".%lu.%llu.tmp", GetCurrentProcessId(), GetTickCount64());
    const std::wstring temporary = path + suffix;
    HANDLE file = CreateFileW(temporary.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
                              FILE_ATTRIBUTE_TEMPORARY, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        error = L"无法写入缓存：" + WindowsErrorMessage(GetLastError());
        return false;
    }
    DWORD total = 0;
    bool success = true;
    while (total < content.size()) {
        DWORD written = 0;
        const DWORD chunk = static_cast<DWORD>(std::min<size_t>(content.size() - total, 1 << 20));
        if (!WriteFile(file, content.data() + total, chunk, &written, nullptr) || !written) { success = false; break; }
        total += written;
    }
    FlushFileBuffers(file);
    CloseHandle(file);
    if (!success || !MoveFileExW(temporary.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
        error = L"保存缓存失败：" + WindowsErrorMessage(GetLastError());
        DeleteFileW(temporary.c_str());
        return false;
    }
    return true;
}

bool CopyDirectoryJson(const std::wstring& source, const std::wstring& destination) {
    if (!DirectoryExists(source) || !EnsureDirectory(destination)) return false;
    WIN32_FIND_DATAW data = {};
    HANDLE find = FindFirstFileW(JoinPath(source, L"*.json").c_str(), &data);
    if (find == INVALID_HANDLE_VALUE) return true;
    do {
        if ((data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
            CopyFileW(JoinPath(source, data.cFileName).c_str(), JoinPath(destination, data.cFileName).c_str(), TRUE);
        }
    } while (FindNextFileW(find, &data));
    FindClose(find);
    return true;
}

std::wstring ApplicationDataDirectory() {
    wchar_t local[MAX_PATH] = {};
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_LOCAL_APPDATA, nullptr, SHGFP_TYPE_CURRENT, local)))
        return JoinPath(local, L"GitLocal");
    return JoinPath(ExeDirectory(), L"data");
}

std::wstring FindProjectFile(const std::wstring& name) {
    std::wstring directory = ExeDirectory();
    for (int depth = 0; depth < 5 && !directory.empty(); ++depth) {
        const std::wstring candidate = JoinPath(directory, name);
        if (PathExists(candidate)) return candidate;
        const std::wstring parent = ParentPath(directory);
        if (parent == directory) break;
        directory = parent;
    }
    return L"";
}

std::vector<std::wstring> LocalDriveRoots() {
    std::vector<std::wstring> roots;
    wchar_t buffer[512] = {};
    const DWORD length = GetLogicalDriveStringsW(_countof(buffer), buffer);
    if (!length || length >= _countof(buffer)) return roots;
    for (const wchar_t* drive = buffer; *drive; drive += wcslen(drive) + 1) {
        const UINT kind = GetDriveTypeW(drive);
        if (kind == DRIVE_FIXED || kind == DRIVE_REMOVABLE) roots.push_back(drive);
    }
    return roots;
}

std::string IsoTimestampUtc() {
    SYSTEMTIME time = {};
    GetSystemTime(&time);
    char buffer[64];
    std::snprintf(buffer, sizeof(buffer), "%04u-%02u-%02uT%02u:%02u:%02u.%03uZ",
                  time.wYear, time.wMonth, time.wDay, time.wHour, time.wMinute, time.wSecond, time.wMilliseconds);
    return buffer;
}
