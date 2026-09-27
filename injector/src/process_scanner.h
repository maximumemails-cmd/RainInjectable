#pragma once

#include <string>
#include <vector>
#include <windows.h>

namespace rain {

struct McProcess {
    DWORD pid = 0;
    DWORD parentPid = 0;
    std::wstring exeName;
    std::wstring exePath;
    std::wstring parentExeName;
    std::wstring windowTitle;
    std::wstring commandLine;
    std::wstring jvmPath;
    bool hasJvm = false;
    bool hasLwjgl = false;
    bool x64 = true;
    bool architectureKnown = false;
    DWORD architectureError = 0;
    DWORD moduleError = 0;
};

class ProcessScanner {
public:
    std::vector<McProcess> scan();

private:
    bool inspectProcess(DWORD pid, DWORD parentPid, const std::wstring& parentName, McProcess& out);
    std::wstring readCommandLine(HANDLE hProc);
};

} // namespace rain
