#include "logger.h"

#include <cstdarg>
#include <cstdio>
#include <ctime>
#include <shlobj.h>
#include <vector>

namespace lion {

Logger& Logger::get() {
    static Logger inst;
    return inst;
}

void Logger::init(const std::wstring& logDir) {
    if (initialised_) return;
    InitializeCriticalSection(&lock_);
    initialised_ = true;
    if (logDir.empty()) return;

    CreateDirectoryW(logDir.c_str(), nullptr);
    SYSTEMTIME st; GetLocalTime(&st);
    wchar_t name[MAX_PATH];
    swprintf_s(name, L"%ls\\injector_%04d%02d%02d_%02d%02d%02d.log",
        logDir.c_str(), st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
    logFile_ = name;
    fileHandle_ = CreateFileW(logFile_.c_str(), GENERIC_WRITE, FILE_SHARE_READ,
        nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
}

void Logger::attachUiSink(HWND edit) {
    uiEdit_ = edit;
}

static const char* levelTag(LogLevel l) {
    switch (l) {
        case LogLevel::TRACE: return "TRACE";
        case LogLevel::INFO:  return "INFO ";
        case LogLevel::WARN:  return "WARN ";
        case LogLevel::ERR:   return "ERROR";
        case LogLevel::CRIT:  return "CRIT ";
    }
    return "?";
}

void Logger::writeLine(LogLevel level, const std::string& msg) {
    SYSTEMTIME st; GetLocalTime(&st);
    char header[64];
    sprintf_s(header, "[%02d:%02d:%02d.%03d][%s] ",
        st.wHour, st.wMinute, st.wSecond, st.wMilliseconds, levelTag(level));

    std::string line = std::string(header) + msg + "\r\n";

    if (fileHandle_ != INVALID_HANDLE_VALUE) {
        DWORD written = 0;
        WriteFile(fileHandle_, line.data(), (DWORD)line.size(), &written, nullptr);
        FlushFileBuffers(fileHandle_);
    }
    OutputDebugStringA(line.c_str());
    if (uiEdit_) appendToUi(line);
}

void Logger::appendToUi(const std::string& line) {
    int len = GetWindowTextLengthA(uiEdit_);
    SendMessageA(uiEdit_, EM_SETSEL, (WPARAM)len, (LPARAM)len);
    SendMessageA(uiEdit_, EM_REPLACESEL, FALSE, (LPARAM)line.c_str());
}

void Logger::log(LogLevel level, const std::string& msg) {
    if (!initialised_) {
        OutputDebugStringA((msg + "\n").c_str());
        return;
    }
    EnterCriticalSection(&lock_);
    writeLine(level, msg);
    LeaveCriticalSection(&lock_);
}

void Logger::logf(LogLevel level, const char* fmt, ...) {
    char buf[2048];
    va_list ap; va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    log(level, buf);
}

std::string lastErrorString(DWORD err) {
    if (!err) return "(no error)";
    LPSTR msg = nullptr;
    DWORD len = FormatMessageA(
        FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM |
            FORMAT_MESSAGE_IGNORE_INSERTS,
        nullptr, err, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        (LPSTR)&msg, 0, nullptr);
    std::string out;
    char prefix[32]; sprintf_s(prefix, "(0x%08lX) ", err);
    out = prefix;
    if (len && msg) {
        out += msg;
        while (!out.empty() && (out.back() == '\r' || out.back() == '\n')) out.pop_back();
        LocalFree(msg);
    } else {
        out += "unknown";
    }
    return out;
}

std::string wideToUtf8(const std::wstring& w) {
    if (w.empty()) return {};
    int sz = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
                                  nullptr, 0, nullptr, nullptr);
    if (sz <= 0) return {};
    std::string out((size_t)sz, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
                        out.data(), sz, nullptr, nullptr);
    return out;
}

std::wstring expandTempDir(const std::wstring& sub) {
    wchar_t buf[MAX_PATH];
    DWORD n = GetTempPathW(MAX_PATH, buf);
    if (!n) return L".\\" + sub;
    std::wstring p = buf;
    if (!p.empty() && p.back() != L'\\') p += L'\\';
    p += sub;
    return p;
}

} // namespace lion
