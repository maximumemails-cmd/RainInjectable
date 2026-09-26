#include "process_scanner.h"
#include "logger.h"

#include <psapi.h>
#include <tlhelp32.h>
#include <winternl.h>   // declares NtQueryInformationProcess, PEB, PROCESSINFOCLASS, etc.
#include <algorithm>
#include <cwctype>

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
    return icontains(exe, L"javaw.exe") || icontains(exe, L"java.exe");
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

LauncherKind ProcessScanner::classify(const McProcess& p, const std::wstring& cmdLine) {
    // Forge: launchwrapper main class plus a Forge marker on the classpath.
    if (icontains(cmdLine, L"net.minecraft.launchwrapper.Launch") &&
        (icontains(cmdLine, L"fmltweaker") ||
         icontains(cmdLine, L"minecraftforge") ||
         icontains(cmdLine, L"forge")))
        return LauncherKind::Forge;
    if (icontains(cmdLine, L"net.fabricmc") || icontains(cmdLine, L"fabric-loader"))
        return LauncherKind::Fabric;
    if (icontains(cmdLine, L"optifine"))
        return LauncherKind::OptiFine;
    if (icontains(cmdLine, L"net.minecraft") || icontains(p.windowTitle, L"Minecraft"))
        return LauncherKind::Vanilla;
    return LauncherKind::Unknown;
}

bool ProcessScanner::inspectProcess(DWORD pid, McProcess& out) {
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
    out.exePath = imageName;
    auto pos = out.exePath.find_last_of(L"\\/");
    out.exeName = (pos == std::wstring::npos) ? out.exePath : out.exePath.substr(pos + 1);

    if (!isJvmHostProcess(out.exeName)) {
        CloseHandle(h);
        return false;
    }

    BOOL isWow64 = FALSE;
    IsWow64Process(h, &isWow64);
    out.x64 = !isWow64;

    // Inspect loaded modules to detect jvm.dll / lwjgl.
    HMODULE mods[1024]; DWORD needed = 0;
    if (EnumProcessModulesEx(h, mods, sizeof(mods), &needed, LIST_MODULES_ALL)) {
        DWORD count = needed / sizeof(HMODULE);
        for (DWORD i = 0; i < count; ++i) {
            wchar_t modName[MAX_PATH] = {};
            if (GetModuleBaseNameW(h, mods[i], modName, MAX_PATH)) {
                std::wstring m = modName;
                if (icontains(m, L"jvm.dll")) out.hasJvm = true;
                if (icontains(m, L"lwjgl"))   out.hasLwjgl = true;
            }
        }
    }

    out.windowTitle = findWindowTitle(pid);
    std::wstring cmd = readCommandLine(h);
    out.launcher = classify(out, cmd);

    CloseHandle(h);

    // Only return processes that look Minecraft-related.
    bool looksLikeMc =
        out.hasJvm &&
        (out.hasLwjgl ||
         out.launcher != LauncherKind::Unknown ||
         icontains(out.windowTitle, L"Minecraft") ||
         icontains(cmd, L"minecraft"));
    return looksLikeMc;
}

std::vector<McProcess> ProcessScanner::scan() {
    std::vector<McProcess> result;
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) {
        LOG_E("CreateToolhelp32Snapshot failed: %s", lastErrorString().c_str());
        return result;
    }
    PROCESSENTRY32W pe{ sizeof(pe) };
    if (Process32FirstW(snap, &pe)) {
        do {
            McProcess mc;
            if (inspectProcess(pe.th32ProcessID, mc)) {
                result.push_back(std::move(mc));
            }
        } while (Process32NextW(snap, &pe));
    }
    CloseHandle(snap);
    LOG_I("Process scan found %zu candidate Minecraft process(es).", result.size());
    return result;
}

const char* ProcessScanner::launcherName(LauncherKind k) const {
    switch (k) {
        case LauncherKind::Vanilla:  return "Vanilla";
        case LauncherKind::Forge:    return "Forge";
        case LauncherKind::Fabric:   return "Fabric";
        case LauncherKind::OptiFine: return "OptiFine";
        default:                     return "Unknown";
    }
}

} // namespace rain
