#include "jvm_loader.h"

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace rain::payload {
namespace {
HMODULE gSelfModule = nullptr;
HANDLE gLogFile = INVALID_HANDLE_VALUE;
CRITICAL_SECTION gLogLock;
bool gLogInit = false;

void ensureLogInit() {
    if (gLogInit) return;
    InitializeCriticalSection(&gLogLock);
    gLogInit = true;
}

jstring javaString(JNIEnv* env, const std::wstring& value) {
    // NewStringUTF expects modified UTF-8; Windows paths are already UTF-16.
    return env->NewString(reinterpret_cast<const jchar*>(value.data()), (jsize)value.size());
}

std::string exceptionText(JNIEnv* env, const char* stage) {
    if (!env->ExceptionCheck()) return stage;
    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();
    std::string result = stage;
    if (exception) {
        jclass type = env->GetObjectClass(exception);
        jmethodID method = type ? env->GetMethodID(type, "toString", "()Ljava/lang/String;") : nullptr;
        if (method) {
            auto description = (jstring)env->CallObjectMethod(exception, method);
            if (!env->ExceptionCheck() && description) {
                const char* chars = env->GetStringUTFChars(description, nullptr);
                if (chars) { result += ": "; result += chars; env->ReleaseStringUTFChars(description, chars); }
                env->DeleteLocalRef(description);
            }
        }
        env->ExceptionClear();
        if (type) env->DeleteLocalRef(type);
        env->DeleteLocalRef(exception);
    }
    payloadLog("ERROR [%s]: %s", stage, result.c_str());
    return result;
}

using GetCreatedVms = jint (JNICALL *)(JavaVM**, jsize, jsize*);
GetCreatedVms findJvmEntry() {
    const ULONGLONG deadline = GetTickCount64() + 15000;
    while (GetTickCount64() < deadline) {
        if (HMODULE jvm = GetModuleHandleW(L"jvm.dll")) {
            auto fn = (GetCreatedVms)GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
            if (fn) return fn;
        }
        Sleep(150);
    }
    return nullptr;
}
} // namespace

std::wstring payloadDir() {
    std::vector<wchar_t> path(512);
    for (;;) {
        DWORD length = GetModuleFileNameW(gSelfModule, path.data(), (DWORD)path.size());
        if (!length) return L".";
        if (length < path.size() - 1) {
            std::wstring result(path.data(), length);
            auto separator = result.find_last_of(L"\\/");
            return separator == std::wstring::npos ? L"." : result.substr(0, separator);
        }
        path.resize(path.size() * 2);
    }
}

void payloadStatus(const char* stage, const std::string& message,
                   const char* code, const char* nextAction) {
    const std::wstring path = payloadDir() + L"\\rain-status-" + std::to_wstring(GetCurrentProcessId()) + L".txt";
    const std::wstring temporary = path + L".tmp";
    HANDLE file = CreateFileW(temporary.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return;
    std::string safe = message;
    for (char& c : safe) if (c == '\r' || c == '\n') c = ' ';
    const std::string contents = std::string(stage) + "\n" + safe + "\n" + code + "\n" + nextAction;
    DWORD written = 0;
    bool ok = WriteFile(file, contents.data(), (DWORD)contents.size(), &written, nullptr) && written == contents.size();
    CloseHandle(file);
    if (ok) MoveFileExW(temporary.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH);
    else DeleteFileW(temporary.c_str());
}

void payloadOpenLog(const std::wstring& path) {
    ensureLogInit();
    EnterCriticalSection(&gLogLock);
    if (gLogFile != INVALID_HANDLE_VALUE) CloseHandle(gLogFile);
    gLogFile = CreateFileW(path.c_str(), GENERIC_WRITE, FILE_SHARE_READ,
                           nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    LeaveCriticalSection(&gLogLock);
}

void payloadLog(const char* fmt, ...) {
    ensureLogInit();
    char buffer[2048];
    va_list args; va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    SYSTEMTIME now; GetLocalTime(&now);
    char header[64];
    sprintf_s(header, "[%02d:%02d:%02d.%03d] ", now.wHour, now.wMinute, now.wSecond, now.wMilliseconds);
    EnterCriticalSection(&gLogLock);
    if (gLogFile != INVALID_HANDLE_VALUE) {
        DWORD written = 0;
        WriteFile(gLogFile, header, (DWORD)strlen(header), &written, nullptr);
        WriteFile(gLogFile, buffer, (DWORD)strlen(buffer), &written, nullptr);
        WriteFile(gLogFile, "\r\n", 2, &written, nullptr);
    }
    OutputDebugStringA(header);
    OutputDebugStringA(buffer);
    OutputDebugStringA("\n");
    LeaveCriticalSection(&gLogLock);
}

extern "C" void payload_setSelfModule(HMODULE module) {
    gSelfModule = module;
    ensureLogInit();
}

bool bootstrap(const std::wstring& jarPath, const std::wstring& dllPath, std::string& error) {
    payloadStatus("JVM", "Waiting for the game's Java VM.");
    if (GetFileAttributesW(jarPath.c_str()) == INVALID_FILE_ATTRIBUTES) {
        error = "Rain runtime JAR is missing from the extracted payload.";
        return false;
    }
    auto getVms = findJvmEntry();
    if (!getVms) { error = "jvm.dll was unavailable after 15 seconds."; return false; }
    JavaVM* vms[8]{}; jsize count = 0;
    jint rc = getVms(vms, 8, &count);
    if (rc != JNI_OK || count == 0) { error = "The Java VM has not finished initialising."; return false; }
    JavaVM* vm = vms[0];
    JNIEnv* env = nullptr;
    JavaVMAttachArgs args{JNI_VERSION_1_6, (char*)"Rain-Bootstrap", nullptr};
    rc = vm->AttachCurrentThread((void**)&env, &args);
    if (rc != JNI_OK || !env) { error = "Could not attach the Rain bootstrap thread to Java (JNI " + std::to_string(rc) + ")."; return false; }
    bool ok = false;
    do {
        payloadStatus("Payload", "Opening the embedded Rain JAR.");
        // Java File.toURI().toURL() escapes paths correctly, including spaces.
        jclass fileClass = env->FindClass("java/io/File");
        if (!fileClass) { error = exceptionText(env, "Loading java.io.File"); break; }
        jmethodID fileCtor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
        if (!fileCtor) { error = exceptionText(env, "Finding File constructor"); break; }
        jstring jJar = javaString(env, jarPath);
        jobject file = env->NewObject(fileClass, fileCtor, jJar);
        if (!file || env->ExceptionCheck()) { error = exceptionText(env, "Creating Java File"); break; }
        jmethodID toUri = env->GetMethodID(fileClass, "toURI", "()Ljava/net/URI;");
        jobject uri = toUri ? env->CallObjectMethod(file, toUri) : nullptr;
        if (!uri || env->ExceptionCheck()) { error = exceptionText(env, "Converting JAR path to URI"); break; }
        jclass uriClass = env->FindClass("java/net/URI");
        jmethodID toUrl = uriClass ? env->GetMethodID(uriClass, "toURL", "()Ljava/net/URL;") : nullptr;
        jobject url = toUrl ? env->CallObjectMethod(uri, toUrl) : nullptr;
        if (!url || env->ExceptionCheck()) { error = exceptionText(env, "Converting JAR URI to URL"); break; }
        jclass urlClass = env->FindClass("java/net/URL");
        jobjectArray urls = urlClass ? env->NewObjectArray(1, urlClass, url) : nullptr;
        jclass loaderClass = env->FindClass("java/net/URLClassLoader");
        jmethodID loaderCtor = loaderClass ? env->GetMethodID(loaderClass, "<init>", "([Ljava/net/URL;)V") : nullptr;
        jobject loader = loaderCtor ? env->NewObject(loaderClass, loaderCtor, urls) : nullptr;
        if (!loader || env->ExceptionCheck()) { error = exceptionText(env, "Creating Rain class loader"); break; }
        jmethodID loadClass = env->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        jstring bootstrapName = env->NewStringUTF("first.rain.anticheat.bootstrap.RainBootstrap");
        auto bootstrapClass = loadClass ? (jclass)env->CallObjectMethod(loader, loadClass, bootstrapName) : nullptr;
        if (!bootstrapClass || env->ExceptionCheck()) { error = exceptionText(env, "Loading RainBootstrap class"); break; }
        jmethodID start = env->GetStaticMethodID(bootstrapClass, "start", "(Ljava/lang/String;Ljava/lang/String;)V");
        if (!start || env->ExceptionCheck()) { error = exceptionText(env, "Finding RainBootstrap.start"); break; }
        payloadStatus("Bootstrap", "Inspecting the game's Java runtime.");
        jstring jDll = javaString(env, dllPath);
        env->CallStaticVoidMethod(bootstrapClass, start, jJar, jDll);
        if (env->ExceptionCheck()) { error = exceptionText(env, "Starting Rain"); break; }
        ok = true;
    } while (false);
    vm->DetachCurrentThread();
    return ok;
}
} // namespace rain::payload
