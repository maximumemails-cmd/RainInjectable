#pragma once

#include <string>
#include <windows.h>

namespace lion {

enum class LogLevel { TRACE, INFO, WARN, ERR, CRIT };

class Logger {
public:
    static Logger& get();

    void init(const std::wstring& logDir);
    void attachUiSink(HWND edit);

    void log(LogLevel level, const std::string& msg);
    void logf(LogLevel level, const char* fmt, ...);

    std::wstring logFilePath() const { return logFile_; }

private:
    Logger() = default;
    void writeLine(LogLevel level, const std::string& msg);
    void appendToUi(const std::string& line);

    HWND uiEdit_ = nullptr;
    std::wstring logFile_;
    HANDLE fileHandle_ = INVALID_HANDLE_VALUE;
    CRITICAL_SECTION lock_{};
    bool initialised_ = false;
};

#define LOG_T(...) ::lion::Logger::get().logf(::lion::LogLevel::TRACE, __VA_ARGS__)
#define LOG_I(...) ::lion::Logger::get().logf(::lion::LogLevel::INFO,  __VA_ARGS__)
#define LOG_W(...) ::lion::Logger::get().logf(::lion::LogLevel::WARN,  __VA_ARGS__)
#define LOG_E(...) ::lion::Logger::get().logf(::lion::LogLevel::ERR,   __VA_ARGS__)
#define LOG_C(...) ::lion::Logger::get().logf(::lion::LogLevel::CRIT,  __VA_ARGS__)

std::string lastErrorString(DWORD err = GetLastError());
std::wstring expandTempDir(const std::wstring& sub);
std::string  wideToUtf8(const std::wstring& w);

} // namespace lion
