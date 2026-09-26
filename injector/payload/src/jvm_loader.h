#pragma once

#include <jni.h>
#include <string>
#include <windows.h>

namespace rain::payload {

// Single-phase bootstrap. rain-payload.dll's DllMain worker thread calls this,
// passing the absolute paths to the rain-runtime.jar and the payload DLL itself.
// Flow: wait for jvm.dll -> JNI_GetCreatedJavaVMs -> AttachCurrentThread ->
// URLClassLoader(jar) -> RainBootstrap.start(jar, dll). Everything else
// (Forge hookery, modules, rendering) is on the Java side.
bool bootstrap(const std::wstring& jarPath,
               const std::wstring& dllPath,
               std::string& error);

// Where the payload DLL was loaded from (used for sibling log files).
std::wstring payloadDir();

// Opens (creating if needed) the append-only payload log file at `path`.
// Must be called before payloadLog() will write to disk.
void payloadOpenLog(const std::wstring& path);

// Append-only line into the payload log next to the DLL.
void payloadLog(const char* fmt, ...);

} // namespace rain::payload
