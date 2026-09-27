#include "embedded_payload.h"

#include <windows.h>
#include <shlobj.h>

#include <cstdint>
#include <algorithm>
#include <cstring>
#include <string>

namespace rain {
namespace {

struct EmbeddedFile {
    const unsigned char* data = nullptr;
    DWORD size = 0;
};

bool loadResource(WORD id, EmbeddedFile& file) {
    HMODULE exe = GetModuleHandleW(nullptr);
    HRSRC resource = FindResourceW(exe, MAKEINTRESOURCEW(id), RT_RCDATA);
    if (!resource) return false;
    HGLOBAL loaded = LoadResource(exe, resource);
    if (!loaded) return false;
    file.data = static_cast<const unsigned char*>(LockResource(loaded));
    file.size = SizeofResource(exe, resource);
    return file.data && file.size;
}

uint64_t hashBytes(uint64_t hash, const EmbeddedFile& file) {
    for (DWORD i = 0; i < file.size; ++i) {
        hash = (hash ^ file.data[i]) * UINT64_C(1099511628211);
    }
    return hash;
}

bool ensureDirectory(const std::wstring& path) {
    return CreateDirectoryW(path.c_str(), nullptr) || GetLastError() == ERROR_ALREADY_EXISTS;
}

bool fileMatches(const std::wstring& path, const EmbeddedFile& expected) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                              nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return false;
    LARGE_INTEGER size{};
    bool match = GetFileSizeEx(file, &size) && size.QuadPart == expected.size;
    unsigned char buffer[16384];
    DWORD offset = 0;
    while (match && offset < expected.size) {
        DWORD count = std::min(static_cast<DWORD>(sizeof(buffer)), expected.size - offset);
        DWORD read = 0;
        match = ReadFile(file, buffer, count, &read, nullptr) && read == count &&
                std::memcmp(buffer, expected.data + offset, count) == 0;
        offset += count;
    }
    CloseHandle(file);
    return match;
}

bool extractFile(const std::wstring& directory, const wchar_t* name,
                 const EmbeddedFile& contents, std::wstring& error) {
    const std::wstring destination = directory + L"\\" + name;
    if (fileMatches(destination, contents)) return true;

    wchar_t temporary[MAX_PATH] = {};
    if (!GetTempFileNameW(directory.c_str(), L"rni", 0, temporary)) {
        error = L"Could not create a temporary payload file (Windows error " +
                std::to_wstring(GetLastError()) + L").";
        return false;
    }
    HANDLE output = CreateFileW(temporary, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
                                FILE_ATTRIBUTE_NORMAL, nullptr);
    if (output == INVALID_HANDLE_VALUE) {
        error = L"Could not open the temporary payload file (Windows error " +
                std::to_wstring(GetLastError()) + L").";
        DeleteFileW(temporary);
        return false;
    }
    DWORD written = 0;
    bool ok = WriteFile(output, contents.data, contents.size, &written, nullptr) &&
              written == contents.size && FlushFileBuffers(output);
    CloseHandle(output);
    if (ok) {
        ok = MoveFileExW(temporary, destination.c_str(),
                         MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH) != 0;
    }
    if (!ok) {
        error = L"Could not extract " + std::wstring(name) + L" (Windows error " +
                std::to_wstring(GetLastError()) + L").";
        DeleteFileW(temporary);
    }
    return ok;
}

} // namespace

bool extractEmbeddedPayload(std::wstring& dllPath, std::wstring& jarPath,
                            std::wstring& error) {
    EmbeddedFile dll, jar, badlionJar;
    if (!loadResource(101, dll) || !loadResource(102, jar) || !loadResource(103, badlionJar)) {
        // Fallback to local files next to executable if running unpacked or during testing
        wchar_t exePath[MAX_PATH] = {};
        GetModuleFileNameW(nullptr, exePath, MAX_PATH);
        wchar_t* slash = wcsrchr(exePath, L'\\');
        if (slash) *slash = L'\0';
        std::wstring localDll = std::wstring(exePath) + L"\\rain-payload.dll";
        std::wstring localJar = std::wstring(exePath) + L"\\rain-runtime.jar";
        std::wstring localBadlion = std::wstring(exePath) + L"\\rain-badlion.jar";
        if (GetFileAttributesW(localDll.c_str()) != INVALID_FILE_ATTRIBUTES &&
            GetFileAttributesW(localJar.c_str()) != INVALID_FILE_ATTRIBUTES &&
            GetFileAttributesW(localBadlion.c_str()) != INVALID_FILE_ATTRIBUTES) {
            dllPath = localDll;
            jarPath = localJar;
            return true;
        }
        error = L"This RainInjectable.exe does not contain all runtime payloads.";
        return false;
    }
    wchar_t localAppData[MAX_PATH] = {};
    if (FAILED(SHGetFolderPathW(nullptr, CSIDL_LOCAL_APPDATA | CSIDL_FLAG_CREATE,
                                nullptr, SHGFP_TYPE_CURRENT, localAppData))) {
        error = L"Could not locate Local AppData for payload extraction.";
        return false;
    }
    uint64_t hash = hashBytes(hashBytes(hashBytes(UINT64_C(14695981039346656037), dll), jar), badlionJar);
    wchar_t version[24] = {};
    swprintf_s(version, L"v-%016llx", static_cast<unsigned long long>(hash));
    const std::wstring base = std::wstring(localAppData) + L"\\RainInjectable";
    const std::wstring directory = base + L"\\" + version;
    if (!ensureDirectory(base) || !ensureDirectory(directory)) {
        error = L"Could not create the RainInjectable payload cache (Windows error " +
                std::to_wstring(GetLastError()) + L").";
        return false;
    }
    dllPath = directory + L"\\rain-payload.dll";
    jarPath = directory + L"\\rain-runtime.jar";
    return extractFile(directory, L"rain-payload.dll", dll, error) &&
           extractFile(directory, L"rain-runtime.jar", jar, error) &&
           extractFile(directory, L"rain-badlion.jar", badlionJar, error);
}

} // namespace rain
