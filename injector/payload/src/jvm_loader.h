#pragma once

#include <jni.h>
#include <string>
#include <windows.h>

namespace lion::payload {

// Single-phase bootstrap. payload.dll's DllMain worker thread calls this,
// passing the absolute paths to the client jar and the payload DLL itself.
// Internally we find jvm.dll, locate the JVM's bundled instrument.dll, and
// call Agent_OnAttach with "<jar>=<dll>" — the JVM then loads our jar as a
// Java agent and invokes lion.client.LionAgent.agentmain. From there
// everything else (LaunchClassLoader registration, AgentRuntime, modules,
// ESP rendering) is on the Java side.
bool bootstrap(const std::wstring& jarPath,
               const std::wstring& dllPath,
               std::string& error);

// Where the payload DLL was loaded from (used for sibling log files).
std::wstring payloadDir();

// Append-only line into payload.log next to the DLL.
void payloadLog(const char* fmt, ...);

} // namespace lion::payload
