#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <string>

#include "jvm_loader.h"

extern "C" void payload_setSelfModule(HMODULE m);

namespace {

HMODULE gMod = nullptr;

DWORD WINAPI worker(LPVOID) {
    using namespace rain::payload;
    const std::wstring dir = payloadDir();
    const std::wstring jar = dir + L"\\rain-runtime.jar";
    const std::wstring dll = dir + L"\\rain-payload.dll";

    payloadOpenLog(dir + L"\\rain-payload-" + std::to_wstring(GetCurrentProcessId()) + L".log");

    std::string err;
    bool ok = bootstrap(jar, dll, err);
    if (ok) {
        payloadLog("Bootstrap succeeded.");
        payloadStatus("Complete", "Rain runtime initialized.");
    } else {
        payloadLog("Bootstrap failed: %s", err.c_str());
        const char* code = "BOOTSTRAP_FAILED";
        const char* action = "Inspect the payload and Java bootstrap logs.";
        if (err.find("FORGE_FRAMEWORK_MISSING") != std::string::npos ||
            err.find("Forge not detected") != std::string::npos ||
            err.find("not a LaunchWrapper") != std::string::npos) {
            code = "FORGE_UNAVAILABLE";
            action = "Launch Minecraft with a Forge 1.8.9 profile to enable Rain.";
        } else if (err.find("CLASSLOADER_NOT_FOUND") != std::string::npos) {
            code = "CLASSLOADER_UNAVAILABLE";
            action = "Ensure the game client thread is running and active.";
        } else if (err.find("JNI ") != std::string::npos || err.find("Java VM") != std::string::npos) {
            code = "JVM_ATTACH_FAILED";
            action = "Inspect the JNI result and target JVM state.";
        }
        payloadStatus("Failed", err, code, action);
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
        if (!t) return FALSE;
        CloseHandle(t);
    }
    return TRUE;
}
