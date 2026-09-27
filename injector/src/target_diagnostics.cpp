#include "compatibility.h"
#include "process_scanner.h"
#include "injector.h"
#include "status_protocol.h"
#include <cstdio>
#include <string>
#include <vector>
#include <windows.h>

static std::wstring getExeDir() {
    wchar_t buf[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, buf, MAX_PATH);
    wchar_t* slash = wcsrchr(buf, L'\\');
    if (slash) *slash = L'\0';
    return buf;
}

static bool readStatusFile(const std::wstring& path, rain::PayloadStatus& status) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                              nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return false;
    char buffer[8192]{}; DWORD size = 0;
    bool ok = ReadFile(file, buffer, sizeof(buffer) - 1, &size, nullptr) && size > 0;
    CloseHandle(file);
    if (!ok) return false;
    return rain::parsePayloadStatus(std::string(buffer, size), status);
}

int wmain(int argc, wchar_t* argv[]) {
    bool doInject = false;
    DWORD targetPid = 0;
    std::wstring requestedDll;
    for (int i = 1; i < argc; ++i) {
        if (_wcsicmp(argv[i], L"--inject") == 0) {
            doInject = true;
            if (i + 1 < argc && argv[i + 1][0] != L'-') {
                targetPid = (DWORD)_wtoi(argv[++i]);
            }
        } else if (_wcsicmp(argv[i], L"--dll") == 0 && i + 1 < argc) {
            requestedDll = argv[++i];
        }
    }

    rain::ProcessScanner scanner;
    auto processes = scanner.scan();
    std::wprintf(L"[Discovery] %zu Minecraft JVM candidate(s)\n", processes.size());
    const rain::McProcess* selected = nullptr;
    int selectedRank = 999;

    for (const auto& process : processes) {
        auto result = rain::assessCompatibility(process);
        std::wprintf(L"[Discovery] PID %lu, parent PID %lu (%ls), %ls\n",
                     process.pid, process.parentPid, process.parentExeName.c_str(),
                     process.exeName.c_str());
        std::wprintf(L"[Client] %ls\n[JVM] %ls; Java %ls; architecture %ls\n",
                     result.client.c_str(), result.jvm.c_str(),
                     result.javaVersion.c_str(), result.architecture.c_str());
        std::wprintf(L"[Minecraft] %ls\n[Compatibility] %ls: %ls\n",
                     result.version.c_str(), rain::compatibilityName(result.level),
                     result.reason.c_str());
        for (const auto& finding : result.stages) {
            std::wprintf(L"  [%ls] %ls: %ls", rain::stageName(finding.stage),
                         rain::stageStateName(finding.state), finding.reason.c_str());
            if (!finding.code.empty()) std::wprintf(L" [%ls]", finding.code.c_str());
            if (finding.win32Error) std::wprintf(L" (Win32 %lu)", finding.win32Error);
            if (!finding.nextAction.empty()) std::wprintf(L" Next: %ls", finding.nextAction.c_str());
            std::wprintf(L"\n");
        }

        if (targetPid != 0) {
            // Explicit target: exact PID match wins outright.
            if (process.pid == targetPid) selected = &process;
        } else {
            // Auto-target the most injectable instance (Supported Forge 1.8.9
            // ranks best) rather than whichever candidate enumerates first.
            int rank = static_cast<int>(result.level);
            if (rank < selectedRank) {
                selectedRank = rank;
                selected = &process;
            }
        }
    }

    if (doInject) {
        if (!selected) {
            std::wprintf(L"\n[Inject] No matching Minecraft candidate found for injection.\n");
            return 1;
        }

        std::wstring exeDir = getExeDir();
        std::wstring dllPath = requestedDll.empty() ? exeDir + L"\\rain-payload.dll" : requestedDll;
        DWORD attr = GetFileAttributesW(dllPath.c_str());
        if (attr == INVALID_FILE_ATTRIBUTES && requestedDll.empty()) {
            dllPath = exeDir + L"\\..\\..\\dist\\rain-payload.dll";
            attr = GetFileAttributesW(dllPath.c_str());
        }
        if (attr == INVALID_FILE_ATTRIBUTES) {
            std::wprintf(L"\n[Inject] ERROR: rain-payload.dll not found!\n");
            return 1;
        }

        std::wprintf(L"\n[Inject] Attempting injection into PID %lu with DLL: %ls\n",
                     selected->pid, dllPath.c_str());

        std::wstring dir = dllPath;
        auto pos = dir.find_last_of(L"\\/");
        if (pos != std::wstring::npos) dir = dir.substr(0, pos);
        std::wstring statusPath = dir + L"\\rain-status-" + std::to_wstring(selected->pid) + L".txt";
        DeleteFileW(statusPath.c_str());

        rain::Injector injector;
        auto res = injector.inject(selected->pid, dllPath);
        std::wprintf(L"[Inject] Injector result: %hs (system error: %lu, message: %hs)\n",
                     res.ok ? "SUCCESS" : "FAILED", res.systemError, res.message.c_str());

        if (res.ok) {
            std::wstring logPath = dir + L"\\rain-payload-" + std::to_wstring(selected->pid) + L".log";
            std::wstring bootLogPath = dir + L"\\rain-bootstrap.log";

            std::wprintf(L"[Inject] Polling for payload status at: %ls\n", statusPath.c_str());
            for (int attempt = 0; attempt < 180; ++attempt) {
                Sleep(200);
                rain::PayloadStatus st;
                if (readStatusFile(statusPath, st)) {
                    if (st.stage == L"Complete" || st.stage == L"Failed") {
                        std::wprintf(L"[Inject] Status reached terminal phase [%ls]: %ls (code: %ls, action: %ls)\n",
                                     st.stage.c_str(), st.message.c_str(), st.code.c_str(), st.action.c_str());
                        break;
                    } else if (!st.stage.empty()) {
                        std::wprintf(L"[Inject] Status: [%ls] %ls\n", st.stage.c_str(), st.message.c_str());
                    }
                }
            }

            // Print payload log if available
            FILE* f = nullptr;
            _wfopen_s(&f, logPath.c_str(), L"r");
            if (f) {
                std::wprintf(L"\n--- Payload Log (%ls) ---\n", logPath.c_str());
                char line[512];
                while (fgets(line, sizeof(line), f)) {
                    printf("%s", line);
                }
                fclose(f);
            }

            // Print bootstrap log if available
            FILE* bf = nullptr;
            _wfopen_s(&bf, bootLogPath.c_str(), L"r");
            if (bf) {
                std::wprintf(L"\n--- Bootstrap Log (%ls) ---\n", bootLogPath.c_str());
                char bline[512];
                while (fgets(bline, sizeof(bline), bf)) {
                    printf("%s", bline);
                }
                fclose(bf);
            }
        }
    }

    return 0;
}
