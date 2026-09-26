#include "jvm_loader.h"

#include <cstdarg>
#include <cstdio>
#include <string>

namespace lion::payload {

namespace {

HMODULE gSelfModule = nullptr;
HANDLE  gLogFile    = INVALID_HANDLE_VALUE;
CRITICAL_SECTION gLogLock;
bool gLogInit = false;

void ensureLogInit() {
    if (gLogInit) return;
    InitializeCriticalSection(&gLogLock);
    gLogFile = INVALID_HANDLE_VALUE;
    gLogInit = true;
}

std::string wideToUtf8(const std::wstring& w) {
    if (w.empty()) return {};
    int sz = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
                                  nullptr, 0, nullptr, nullptr);
    std::string out(sz, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
                        out.data(), sz, nullptr, nullptr);
    return out;
}

typedef jint (JNICALL *PFN_JNI_GetCreatedJavaVMs)(JavaVM**, jsize, jsize*);

PFN_JNI_GetCreatedJavaVMs findJvmEntry(int timeoutMs = 30000) {
    DWORD start = GetTickCount();
    while ((GetTickCount() - start) < (DWORD)timeoutMs) {
        HMODULE jvm = GetModuleHandleW(L"jvm.dll");
        if (jvm) {
            auto fn = (PFN_JNI_GetCreatedJavaVMs)GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
            if (fn) return fn;
        }
        Sleep(150);
    }
    return nullptr;
}

void describeAndClearException(JNIEnv* env, const char* where) {
    if (!env->ExceptionCheck()) return;
    jthrowable t = env->ExceptionOccurred();
    env->ExceptionClear();
    payloadLog("[%s] Java exception thrown:", where);
    if (!t) return;
    jclass tc = env->GetObjectClass(t);
    jmethodID toString = env->GetMethodID(tc, "toString", "()Ljava/lang/String;");
    if (toString) {
        auto js = (jstring)env->CallObjectMethod(t, toString);
        if (js) {
            const char* s = env->GetStringUTFChars(js, nullptr);
            payloadLog("  %s", s ? s : "(null)");
            env->ReleaseStringUTFChars(js, s);
        }
    }
    env->DeleteLocalRef(t);
}

} // namespace

std::wstring payloadDir() {
    wchar_t buf[MAX_PATH] = {};
    GetModuleFileNameW(gSelfModule, buf, MAX_PATH);
    std::wstring p = buf;
    auto pos = p.find_last_of(L"\\/");
    return (pos == std::wstring::npos) ? L"." : p.substr(0, pos);
}

void payloadLog(const char* fmt, ...) {
    ensureLogInit();
    char buf[2048];
    va_list ap; va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf) - 16, fmt, ap);
    va_end(ap);
    if (n < 0) n = 0;

    SYSTEMTIME st; GetLocalTime(&st);
    char header[64];
    sprintf_s(header, "[%02d:%02d:%02d.%03d] ", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);

    EnterCriticalSection(&gLogLock);
    if (gLogFile != INVALID_HANDLE_VALUE) {
        DWORD w = 0;
        WriteFile(gLogFile, header, (DWORD)strlen(header), &w, nullptr);
        WriteFile(gLogFile, buf, (DWORD)strlen(buf), &w, nullptr);
        WriteFile(gLogFile, "\r\n", 2, &w, nullptr);
        FlushFileBuffers(gLogFile);
    }
    OutputDebugStringA(header);
    OutputDebugStringA(buf);
    OutputDebugStringA("\n");
    LeaveCriticalSection(&gLogLock);
}

extern "C" void payload_setSelfModule(HMODULE m) {
    gSelfModule = m;
    ensureLogInit();
}

// Bootstrap path: attach to the host JVM, build a URLClassLoader for the
// client jar, and call lion.client.Agent.start(jarPath, dllPath). Everything
// else (LaunchClassLoader hookery, modules, ESP renderer) is handled on the
// Java side — we do NOT touch instrument.dll, because Agent_OnAttach reliably
// segfaults the JVM in PrismLauncher's jre-legacy (Java 8u51).
bool bootstrap(const std::wstring& jarPath,
               const std::wstring& dllPath,
               std::string& error) {
    payloadLog("Payload bootstrap starting (URLClassLoader-only path).");
    payloadLog("  JAR: %s", wideToUtf8(jarPath).c_str());
    payloadLog("  DLL: %s", wideToUtf8(dllPath).c_str());

    if (GetFileAttributesW(jarPath.c_str()) == INVALID_FILE_ATTRIBUTES) {
        error = "client.jar not found at expected path";
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }

    auto getVms = findJvmEntry(30000);
    if (!getVms) {
        error = "jvm.dll did not appear within 30s";
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }
    payloadLog("Resolved JNI_GetCreatedJavaVMs.");

    JavaVM* vms[8]; jsize count = 0;
    jint rc = getVms(vms, 8, &count);
    if (rc != JNI_OK || count == 0) {
        error = "JNI_GetCreatedJavaVMs returned no VM";
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }
    JavaVM* vm = vms[0];

    JNIEnv* env = nullptr;
    jint ar = vm->AttachCurrentThread((void**)&env, nullptr);
    if (ar != JNI_OK || !env) {
        error = "AttachCurrentThread failed: rc=" + std::to_string(ar);
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }
    payloadLog("Attached to JVM (JNIEnv=%p).", (void*)env);

    // Build URLClassLoader for the client jar.
    std::wstring uri = L"file:///";
    for (wchar_t c : jarPath) uri += (c == L'\\') ? L'/' : c;
    std::string uri8 = wideToUtf8(uri);

    jclass urlClass = env->FindClass("java/net/URL");
    if (!urlClass) { describeAndClearException(env, "FindClass URL"); error = "FindClass URL failed"; vm->DetachCurrentThread(); return false; }
    jmethodID urlCtor = env->GetMethodID(urlClass, "<init>", "(Ljava/lang/String;)V");
    jstring jUri = env->NewStringUTF(uri8.c_str());
    jobject urlObj = env->NewObject(urlClass, urlCtor, jUri);
    if (!urlObj || env->ExceptionCheck()) {
        describeAndClearException(env, "new URL");
        error = "Could not construct java.net.URL for jar path";
        vm->DetachCurrentThread();
        return false;
    }

    jobjectArray urls = env->NewObjectArray(1, urlClass, urlObj);
    jclass loaderClass = env->FindClass("java/net/URLClassLoader");
    jmethodID loaderCtor = env->GetMethodID(loaderClass, "<init>", "([Ljava/net/URL;)V");
    jobject loader = env->NewObject(loaderClass, loaderCtor, urls);
    if (!loader || env->ExceptionCheck()) {
        describeAndClearException(env, "new URLClassLoader");
        error = "Could not construct URLClassLoader";
        vm->DetachCurrentThread();
        return false;
    }
    payloadLog("URLClassLoader created.");

    jmethodID loadClass = env->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring agentName = env->NewStringUTF("lion.client.Agent");
    auto agentClass = (jclass)env->CallObjectMethod(loader, loadClass, agentName);
    if (!agentClass || env->ExceptionCheck()) {
        describeAndClearException(env, "loadClass Agent");
        error = "Could not load lion.client.Agent from the JAR";
        vm->DetachCurrentThread();
        return false;
    }
    payloadLog("Loaded lion.client.Agent.");

    jmethodID start = env->GetStaticMethodID(
        agentClass, "start", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (!start) {
        describeAndClearException(env, "GetStaticMethodID Agent.start");
        error = "Agent.start(String,String) not found in JAR";
        vm->DetachCurrentThread();
        return false;
    }
    jstring jJar = env->NewStringUTF(wideToUtf8(jarPath).c_str());
    jstring jDll = env->NewStringUTF(wideToUtf8(dllPath).c_str());
    env->CallStaticVoidMethod(agentClass, start, jJar, jDll);
    if (env->ExceptionCheck()) {
        describeAndClearException(env, "Agent.start");
        error = "lion.client.Agent.start threw — see client.log";
        vm->DetachCurrentThread();
        return false;
    }
    payloadLog("Agent.start returned cleanly.");

    vm->DetachCurrentThread();
    payloadLog("Bootstrap complete.");
    return true;
}

} // namespace lion::payload
