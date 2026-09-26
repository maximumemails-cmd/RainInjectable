#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <string>

#include "jvm_loader.h"

extern "C" void payload_setSelfModule(HMODULE m);

namespace {

HMODULE gMod = nullptr;

std::wstring selfDir() {
    wchar_t buf[MAX_PATH] = {};
    GetModuleFileNameW(gMod, buf, MAX_PATH);
    std::wstring p = buf;
    auto pos = p.find_last_of(L"\\/");
    return (pos == std::wstring::npos) ? L"." : p.substr(0, pos);
}

DWORD WINAPI worker(LPVOID) {
    using namespace rain::payload;
    const std::wstring dir = selfDir();
    const std::wstring jar = dir + L"\\rain-runtime.jar";
    const std::wstring dll = dir + L"\\rain-payload.dll";

    payloadOpenLog(dir + L"\\rain-payload.log");

    std::string err;
    bool ok = bootstrap(jar, dll, err);
    if (ok) {
        payloadLog("Bootstrap succeeded.");
    } else {
        payloadLog("Bootstrap failed: %s", err.c_str());
    }
    return 0;
}

} // namespace

BOOL APIENTRY DllMain(HMODULE mod, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        gMod = mod;
        payload_setSelfModule(mod);
        DisableThreadLibraryCalls(mod);
        HANDLE t = CreateThread(nullptr, 0, worker, nullptr, 0, nullptr);
        if (t) CloseHandle(t);
    }
    return TRUE;
}
