#include "injector.h"
#include "logger.h"

#include <psapi.h>

#pragma comment(lib, "psapi.lib")

namespace rain {

static bool sameArchAsTarget(HANDLE hProc, std::string& err) {
    BOOL targetWow = FALSE, selfWow = FALSE;
    if (!IsWow64Process(hProc, &targetWow)) {
        err = "IsWow64Process(target) failed: " + lastErrorString();
        return false;
    }
    IsWow64Process(GetCurrentProcess(), &selfWow);

    // On 64-bit Windows: !targetWow == 64-bit target, targetWow == 32-bit target.
    bool target64 = !targetWow;
    bool self64   = !selfWow;
    if (target64 != self64) {
        err = target64 ? "Target is 64-bit but injector is 32-bit — rebuild injector as x64."
                       : "Target is 32-bit but injector is 64-bit — rebuild injector as x86.";
        return false;
    }
    return true;
}

InjectionResult Injector::inject(DWORD pid, const std::wstring& dllPath) {
    InjectionResult r;
    LOG_I("Beginning injection. PID=%lu DLL=%ls", pid, dllPath.c_str());

    // 1. Confirm the DLL exists on disk before touching the target.
    DWORD attr = GetFileAttributesW(dllPath.c_str());
    if (attr == INVALID_FILE_ATTRIBUTES) {
        r.systemError = GetLastError();
        r.message = "Payload DLL not found at: " + wideToUtf8(dllPath) +
                    " — " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        return r;
    }

    // 2. Open the target with the rights we need.
    const DWORD access =
        PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION |
        PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ;
    HANDLE hProc = OpenProcess(access, FALSE, pid);
    if (!hProc) {
        r.systemError = GetLastError();
        r.message = "OpenProcess failed (need admin?): " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        return r;
    }
    LOG_T("OpenProcess OK.");

    // 3. Architecture must match.
    std::string archErr;
    if (!sameArchAsTarget(hProc, archErr)) {
        r.message = archErr;
        LOG_E("%s", r.message.c_str());
        CloseHandle(hProc);
        return r;
    }
    LOG_T("Architecture check OK.");

    // 4. Allocate space in target for the DLL path.
    SIZE_T pathBytes = (dllPath.size() + 1) * sizeof(wchar_t);
    void* remoteMem = VirtualAllocEx(hProc, nullptr, pathBytes,
                                     MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remoteMem) {
        r.systemError = GetLastError();
        r.message = "VirtualAllocEx failed: " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        CloseHandle(hProc);
        return r;
    }
    LOG_T("VirtualAllocEx OK at %p (%zu bytes).", remoteMem, pathBytes);

    // 5. Write the path.
    if (!WriteProcessMemory(hProc, remoteMem, dllPath.c_str(), pathBytes, nullptr)) {
        r.systemError = GetLastError();
        r.message = "WriteProcessMemory failed: " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return r;
    }
    LOG_T("WriteProcessMemory OK.");

    // 6. Resolve LoadLibraryW from kernel32 (same address in our process and the target).
    HMODULE k32 = GetModuleHandleW(L"kernel32.dll");
    auto loadLib = (LPTHREAD_START_ROUTINE)GetProcAddress(k32, "LoadLibraryW");
    if (!loadLib) {
        r.systemError = GetLastError();
        r.message = "GetProcAddress(LoadLibraryW) failed: " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return r;
    }

    // 7. Spawn the remote thread.
    HANDLE thread = CreateRemoteThread(hProc, nullptr, 0, loadLib, remoteMem, 0, nullptr);
    if (!thread) {
        r.systemError = GetLastError();
        r.message = "CreateRemoteThread failed: " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return r;
    }
    LOG_T("CreateRemoteThread OK, waiting for completion (30s timeout)...");

    DWORD wait = WaitForSingleObject(thread, 30000);
    if (wait != WAIT_OBJECT_0) {
        r.systemError = GetLastError();
        r.message = "Remote thread did not finish (status=" + std::to_string(wait) +
                    "): " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        CloseHandle(thread);
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return r;
    }

    DWORD exitCode = 0;
    GetExitCodeThread(thread, &exitCode);
    CloseHandle(thread);
    VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);

    if (exitCode == 0) {
        r.message = "LoadLibraryW returned NULL — the DLL failed to load. "
                    "Common causes: dependency missing, wrong architecture, or jvm.dll not loaded yet.";
        LOG_E("%s", r.message.c_str());
        CloseHandle(hProc);
        return r;
    }

    LOG_I("Remote LoadLibraryW returned handle 0x%llX in target.", (unsigned long long)exitCode);

    // 8. Verify by enumerating loaded modules.
    bool present = isPayloadLoaded(pid, dllPath);
    if (!present) {
        r.message = "Remote thread succeeded but module not found in target — partial failure.";
        LOG_W("%s", r.message.c_str());
        CloseHandle(hProc);
        return r;
    }

    CloseHandle(hProc);
    r.ok = true;
    r.message = "Injection complete and verified.";
    LOG_I("%s", r.message.c_str());
    return r;
}

bool Injector::isPayloadLoaded(DWORD pid, const std::wstring& dllPath) {
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
    if (!h) return false;
    HMODULE mods[1024]; DWORD needed = 0;
    bool found = false;
    if (EnumProcessModulesEx(h, mods, sizeof(mods), &needed, LIST_MODULES_ALL)) {
        DWORD count = needed / sizeof(HMODULE);
        for (DWORD i = 0; i < count; ++i) {
            wchar_t fn[MAX_PATH] = {};
            if (GetModuleFileNameExW(h, mods[i], fn, MAX_PATH)) {
                if (_wcsicmp(fn, dllPath.c_str()) == 0) { found = true; break; }
            }
        }
    }
    CloseHandle(h);
    return found;
}

bool isModuleLoaded(DWORD pid, const std::wstring& moduleBaseName) {
    HANDLE h = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
    if (!h) return false;
    HMODULE mods[1024]; DWORD needed = 0;
    bool found = false;
    if (EnumProcessModulesEx(h, mods, sizeof(mods), &needed, LIST_MODULES_ALL)) {
        DWORD count = needed / sizeof(HMODULE);
        for (DWORD i = 0; i < count; ++i) {
            wchar_t name[MAX_PATH] = {};
            if (GetModuleBaseNameW(h, mods[i], name, MAX_PATH)) {
                if (_wcsicmp(name, moduleBaseName.c_str()) == 0) { found = true; break; }
            }
        }
    }
    CloseHandle(h);
    return found;
}

} // namespace rain
