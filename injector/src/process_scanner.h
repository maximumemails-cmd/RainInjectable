#pragma once

#include <string>
#include <vector>
#include <windows.h>

namespace rain {

enum class LauncherKind {
    Unknown,
    Vanilla,
    Forge,
    Fabric,
    OptiFine,
};

struct McProcess {
    DWORD pid = 0;
    std::wstring exeName;
    std::wstring exePath;
    std::wstring windowTitle;
    bool hasJvm = false;
    bool hasLwjgl = false;
    LauncherKind launcher = LauncherKind::Unknown;
    bool x64 = true;
};

class ProcessScanner {
public:
    std::vector<McProcess> scan();
    const char* launcherName(LauncherKind k) const;

private:
    bool inspectProcess(DWORD pid, McProcess& out);
    LauncherKind classify(const McProcess& p, const std::wstring& cmdLine);
    std::wstring readCommandLine(HANDLE hProc);
};

} // namespace rain
