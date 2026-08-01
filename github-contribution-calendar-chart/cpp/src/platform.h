#pragma once

#include <string>
#include <vector>

std::wstring Utf8ToWide(const std::string& value);
std::string WideToUtf8(const std::wstring& value);
std::wstring JoinPath(const std::wstring& left, const std::wstring& right);
std::wstring ExeDirectory();
std::wstring ParentPath(const std::wstring& path);
std::wstring BaseName(const std::wstring& path);
std::wstring Lowercase(const std::wstring& value);
std::wstring FormatInteger(int value);
std::wstring FormatDuration(unsigned long long milliseconds);

bool PathExists(const std::wstring& path);
bool DirectoryExists(const std::wstring& path);
bool EnsureDirectory(const std::wstring& path);
bool ReadUtf8File(const std::wstring& path, std::string& content, std::wstring& error);
bool WriteUtf8FileAtomic(const std::wstring& path, const std::string& content, std::wstring& error);
bool CopyDirectoryJson(const std::wstring& source, const std::wstring& destination);

std::wstring ApplicationDataDirectory();
std::wstring FindProjectFile(const std::wstring& name);
std::vector<std::wstring> LocalDriveRoots();
std::string IsoTimestampUtc();
std::wstring WindowsErrorMessage(unsigned long code);
