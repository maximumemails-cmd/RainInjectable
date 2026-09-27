#include "process_scanner.h"
#include "logger.h"

#include <psapi.h>
#include <tlhelp32.h>
#include <winternl.h>   // declares NtQueryInformationProcess, PEB, PROCESSINFOCLASS, etc.
#include <algorithm>
#include <cwctype>
#include <unordered_map>

#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "ntdll.lib")

namespace rain {

namespace {

bool icontains(const std::wstring& hay, const std::wstring& needle) {
    if (needle.empty()) return true;
    if (hay.size() < needle.size()) return false;
    std::wstring h = hay, n = needle;
    std::transform(h.begin(), h.end(), h.begin(), ::towlower);
    std::transform(n.begin(), n.end(), n.begin(), ::towlower);
    return h.find(n) != std::wstring::npos;
}

bool isJvmHostProcess(const std::wstring& exe) {
    return _wcsicmp(exe.c_str(), L"javaw.exe") == 0 ||
           _wcsicmp(exe.c_str(), L"java.exe") == 0;
}

struct EnumWindowCtx {
    DWORD pid;
    std::wstring title;
};

BOOL CALLBACK enumWinProc(HWND hwnd, LPARAM lp) {
    auto* c = reinterpret_cast<EnumWindowCtx*>(lp);
    DWORD pid = 0; GetWindowThreadProcessId(hwnd, &pid);
    if (pid != c->pid) return TRUE;
    if (!IsWindowVisible(hwnd)) return TRUE;
    wchar_t title[512] = {};
    GetWindowTextW(hwnd, title, 511);
    if (title[0] == 0) return TRUE;
    // Prefer the longest visible title.
    if (wcslen(title) > c->title.size()) c->title = title;
    return TRUE;
}

std::wstring findWindowTitle(DWORD pid) {
    EnumWindowCtx ctx{ pid, L"" };
    EnumWindows(enumWinProc, reinterpret_cast<LPARAM>(&ctx));
    return ctx.title;
}

} // namespace

std::wstring ProcessScanner::readCommandLine(HANDLE hProc) {
    PROCESS_BASIC_INFORMATION pbi{};
    ULONG ret = 0;
    if (NtQueryInformationProcess(hProc, ProcessBasicInformation,
                                  &pbi, sizeof(pbi), &ret) != 0) return L"";
    if (!pbi.PebBaseAddress) return L"";

    PEB peb{};
    if (!ReadProcessMemory(hProc, pbi.PebBaseAddress, &peb, sizeof(peb), nullptr)) return L"";
    RTL_USER_PROCESS_PARAMETERS rupp{};
    if (!ReadProcessMemory(hProc, peb.ProcessParameters, &rupp, sizeof(rupp), nullptr)) return L"";

    if (!rupp.CommandLine.Buffer || rupp.CommandLine.Length == 0) return L"";
    std::wstring out(rupp.CommandLine.Length / sizeof(wchar_t), L'\0');
    if (!ReadProcessMemory(hProc, rupp.CommandLine.Buffer, out.data(),
                           rupp.CommandLine.Length, nullptr)) return L"";
    return out;
}

bool ProcessScanner::inspectProcess(DWORD pid, DWORD parentPid, const std::wstring& parentName, McProcess& out) {
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
    if (!h) {
        // Try a weaker handle for at least the image name.
        h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
        if (!h) return false;
    }

    wchar_t imageName[MAX_PATH] = {};
    DWORD imageNameSize = MAX_PATH;
    if (!QueryFullProcessImageNameW(h, 0, imageName, &imageNameSize)) {
        CloseHandle(h);
        return false;
    }
    out.pid = pid;
    out.parentPid = parentPid;
    out.parentExeName = parentName;
    out.exePath = imageName;
    auto pos = out.exePath.find_last_of(L"\\/");
    out.exeName = (pos == std::wstring::npos) ? out.exePath : out.exePath.substr(pos + 1);

    BOOL isWow64 = FALSE;
    out.architectureKnown = IsWow64Process(h, &isWow64) != 0;
    out.architectureError = out.architectureKnown ? 0 : GetLastError();
    out.x64 = out.architectureKnown && !isWow64;

    // Inspect loaded modules to detect jvm.dll / lwjgl.
    HMODULE mods[1024]; DWORD needed = 0;
    if (EnumProcessModulesEx(h, mods, sizeof(mods), &needed, LIST_MODULES_ALL)) {
        DWORD count = std::min<DWORD>(needed / sizeof(HMODULE), 1024);
        for (DWORD i = 0; i < count; ++i) {
            wchar_t modName[MAX_PATH] = {};
            if (GetModuleBaseNameW(h, mods[i], modName, MAX_PATH)) {
                std::wstring m = modName;
                if (icontains(m, L"jvm.dll")) {
                    out.hasJvm = true;
                    wchar_t path[MAX_PATH] = {};
                    if (GetModuleFileNameExW(h, mods[i], path, MAX_PATH)) out.jvmPath = path;
                }
                if (icontains(m, L"lwjgl"))   out.hasLwjgl = true;
            }
        }
    } else out.moduleError = GetLastError();

    out.windowTitle = findWindowTitle(pid);
    out.commandLine = readCommandLine(h);

    CloseHandle(h);

    // Only return processes that look Minecraft-related.
    bool looksLikeMc =
        (out.hasJvm || isJvmHostProcess(out.exeName)) &&
        (out.hasLwjgl || icontains(out.windowTitle, L"Minecraft") ||
         icontains(out.windowTitle, L"Lunar Client") ||
         icontains(out.windowTitle, L"Badlion Client") ||
         icontains(out.windowTitle, L"Feather") ||
         icontains(out.commandLine, L"net.minecraft") ||
         icontains(out.commandLine, L"lunarclient") ||
         icontains(out.commandLine, L"badlion") ||
         icontains(out.commandLine, L"fmltweaker") ||
         icontains(out.commandLine, L"minecraftforge") ||
         icontains(out.commandLine, L"fabric-loader"));
    return looksLikeMc;
}

std::vector<McProcess> ProcessScanner::scan() {
    std::vector<McProcess> result;
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) {
        LOG_E("CreateToolhelp32Snapshot failed: %s", lastErrorString().c_str());
        return result;
    }
    std::unordered_map<DWORD, std::wstring> names;
    PROCESSENTRY32W pe{ sizeof(pe) };
    if (Process32FirstW(snap, &pe)) {
        do { names.emplace(pe.th32ProcessID, pe.szExeFile); }
        while (Process32NextW(snap, &pe));
    }
    pe.dwSize = sizeof(pe);
    if (Process32FirstW(snap, &pe)) {
        do {
            McProcess mc;
            auto parent = names.find(pe.th32ParentProcessID);
            if (inspectProcess(pe.th32ProcessID, pe.th32ParentProcessID,
                               parent == names.end() ? L"" : parent->second, mc)) {
                result.push_back(std::move(mc));
            }
        } while (Process32NextW(snap, &pe));
    }
    CloseHandle(snap);
    LOG_I("Process scan found %zu candidate Minecraft process(es).", result.size());
    return result;
}

} // namespace rain
