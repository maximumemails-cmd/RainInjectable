#include "injector.h"
#include "logger.h"

#include <psapi.h>
#include <algorithm>

#pragma comment(lib, "psapi.lib")

namespace rain {

static bool sameArchAsTarget(HANDLE hProc, std::string& err) {
    BOOL targetWow = FALSE, selfWow = FALSE;
    if (!IsWow64Process(hProc, &targetWow)) {
        err = "IsWow64Process(target) failed: " + lastErrorString();
        return false;
    }
    if (!IsWow64Process(GetCurrentProcess(), &selfWow)) {
        err = "Could not determine injector architecture: " + lastErrorString();
        return false;
    }

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
        r.message = wait == WAIT_TIMEOUT
            ? "Loading native payload timed out after 30 seconds. The game may be unresponsive."
            : "Waiting for native payload failed: " + lastErrorString(r.systemError);
        LOG_E("%s", r.message.c_str());
        CloseHandle(thread);
        // A timed-out remote thread may still read this path; freeing it here
        // would create a use-after-free inside the target process.
        if (wait != WAIT_TIMEOUT) VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return r;
    }

    DWORD exitCode = 0;
    if (!GetExitCodeThread(thread, &exitCode)) {
        r.systemError = GetLastError();
        r.message = "Could not read native payload load result: " + lastErrorString(r.systemError);
        CloseHandle(thread);
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return r;
    }
    CloseHandle(thread);
    VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);

    // GetExitCodeThread is only a DWORD, so an x64 HMODULE is truncated.
    // The module list is the authoritative native-load check.
    LOG_T("Remote LoadLibraryW thread exited with low DWORD 0x%08lX.", exitCode);
    bool present = isPayloadLoaded(pid, dllPath);
    if (!present) {
        r.message = exitCode == 0
            ? "Loading native payload failed. Check that the DLL is intact and its dependencies are available."
            : "The native load returned, but the payload is absent from the target module list.";
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
        DWORD count = (std::min)(needed / (DWORD)sizeof(HMODULE), DWORD(1024));
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
        DWORD count = (std::min)(needed / (DWORD)sizeof(HMODULE), DWORD(1024));
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
