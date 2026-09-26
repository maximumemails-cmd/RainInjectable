# Architecture review — LionInjectable V1.0.5

Source: `upstream/LionInjectable` @ `6a04238f` (tag `V1.0.5`). License: **GPL-3.0**.

## What it is

A three-stage Windows injector for a Minecraft 1.8.9 cheat client:

```
LionInjectable.exe  --CreateRemoteThread(LoadLibraryW)-->  payload.dll (inside javaw.exe)
                                                              |  JNI_GetCreatedJavaVMs / AttachCurrentThread
                                                              v
                                                         client.jar  (lion.client.Agent.start)
                                                              |  LaunchClassLoader.addURL / BridgeClassLoader
                                                              v
                                                         com.lionclient.LionClient (modules, hooks, ASM transformers)
```

## Component inventory

| Path | Lang | Purpose | Keep for Rain? |
|---|---|---|---|
| `injector/src/main.cpp` | C++ | `wWinMain`, DPI aware, common-controls manifest | keep (rebrand) |
| `injector/src/gui.cpp` (921 l) | C++ | Win32/GDI+ dark UI: process ListView, INJECT/Refresh buttons, log pane, snow animation timer | keep core; drop snow; rebrand; Forge-only gate |
| `injector/src/process_scanner.*` | C++ | Toolhelp32 enum filtered to `java.exe`/`javaw.exe`/`lunarclient.exe`/`minecraft.exe`; reads target PEB command line via `NtQueryInformationProcess` to classify Vanilla/Forge/Fabric/Lunar/Badlion/OptiFine; checks `jvm.dll`/`lwjgl` modules | keep; remove Lunar/Badlion/`lunarclient.exe`; keep Forge classification |
| `injector/src/injector.*` | C++ | Classic `OpenProcess → VirtualAllocEx → WriteProcessMemory → CreateRemoteThread(LoadLibraryW)`; arch check via `IsWow64Process`; post-verify with `EnumProcessModulesEx`. Also `writeConfigJson`, `copyAssetTo` | keep the injector; drop `writeConfigJson`/`copyAssetTo` (unused) |
| `injector/src/logger.*` | C++ | File + UI-edit-box logger → `%TEMP%\LionInjector\injector_*.log` | keep (rename dir) |
| `injector/payload/src/dllmain.cpp` | C++ | `DllMain(PROCESS_ATTACH)` spawns a thread → `RunBootstrap()`; logs to `payload.log` beside the DLL | keep, simplify |
| `injector/payload/src/jvm_loader.*` | C++ | Waits for `jvm.dll`; `JNI_GetCreatedJavaVMs`; `AttachCurrentThread`; builds `URLClassLoader(client.jar, systemCL)`; calls static `lion.client.Agent.start(String jar, String dll)`. Per its own comments, does **not** touch `instrument.dll` on this path | keep; retarget to Rain bootstrap |
| `injector/payload/src/agent_attach.*` | C++ | Loads `instrument.dll` and calls `Agent_OnAttach` **in-process** to obtain a JVMTI `Instrumentation` without the Attach API; explicitly written to work under `-XX:+DisableAttachMechanism` (Lunar/Badlion) | **drop** — defeats launcher hardening; Rain needs no bytecode transformation |
| `injector/payload/src/native_bridge.cpp` | C++ | `Java_lion_client_NativeBridge_*` JNI exports (`attachAgent`, `getLoadedClasses`, `retransform`) | **drop** |
| `client/` (Gradle + shadow) | Java | `lion.client.Agent` (classloader discovery), `LionAgent` (`Premain-Class`/`Agent-Class`, `Can-Retransform-Classes`), `LionTransformer`/`LionDeobfTransformer`/`LionVanillaTransformer` (ASM 9.7 shaded), `NotchMapping`/`LionNotchRemapper` (embedded MCP↔Notch mappings), `Hooks`, `MCAccess`, `LauncherDetection`, `com.lionclient.*` cheat modules | **drop entirely** |
| `build.bat`, `injector/CMakeLists.txt` | build | Gradle shadowJar → CMake/Ninja/MSVC x64 → stage exe+dll+jar | keep the CMake shape; replace Gradle with `javac` |
| `build/`, `client/build/`, `client/.gradle/` | artifacts | committed build outputs (jars, `.obj`, Gradle caches) | ignore |

## Injection flow (as shipped)

1. GUI scans processes; user selects a row and clicks INJECT.
2. `Injector::inject` checks `payload.dll` exists, same bitness, allocs + writes the DLL path, `CreateRemoteThread(LoadLibraryW)`, waits 30 s, verifies module presence.
3. `payload.dll` `DllMain` → new thread → `RunBootstrap()`:
   - locate `client.jar` beside the DLL; wait ≤30 s for `jvm.dll`;
   - `JNI_GetCreatedJavaVMs` → `AttachCurrentThread` (thread named `Lion-Bootstrap`);
   - `new URLClassLoader(new URL[]{jar}, systemClassLoader)`;
   - `Class.forName("lion.client.Agent", true, cl).start(jar, dll)`.
4. `Agent.start` (Java): `System.load(dll)`; optionally `NativeBridge.attachAgent` (JVMTI); find a classloader that can see `net.minecraft.client.Minecraft` — first choice `net.minecraft.launchwrapper.Launch.classLoader` + `addURL(jar)`; registers an `IClassTransformer`; instantiates `com.lionclient.LionClient.bootstrap()`.

## What is reusable for a defensive tool

The *loader* is generic and sound:

- `CreateRemoteThread(LoadLibraryW)` injector with real error reporting (`FormatMessage`), architecture check, 30 s wait and module verification.
- The JNI attach + `URLClassLoader` bootstrap. Robust `jvm.dll` wait loop.
- The `Launch.classLoader.addURL(jar)` trick: loading a jar through Forge's `LaunchClassLoader` means the classes see the same (runtime-deobfuscated, SRG-named) Minecraft/Forge classes that every regular mod sees. **This is exactly what Rain already compiles against** (`setup.ps1` remaps vanilla to SRG), so Rain classes run unmodified.
- The Win32 GUI, logger, and process scanner.

## What must not be carried over

- `agent_attach.*` / `native_bridge.cpp` / `LionAgent` / all ASM transformers and mapping tables — bytecode patching and JVMTI acquisition that sidesteps launcher lockdown. Rain has no hooks; it only subscribes to public Forge events.
- Lunar/Badlion targeting and every `com.lionclient.*` module.
- The committed `build/` trees.

## Risks / observations

- `Agent.start` walks *every* thread's context classloader as a fallback — unnecessary for Forge; we only support `Launch.classLoader`.
- Re-injection: `LoadLibraryW` on an already-loaded DLL does not re-run `DllMain`, so a second INJECT is a silent no-op. Our injector will detect the module already present and say so.
- `payload.dll` is never unloaded; `FreeLibraryAndExitThread` is intentionally not called because the JVM keeps the attached thread. Same in our version.
- No signing; SmartScreen/AV may flag any `CreateRemoteThread` tool. Documented in TESTING.md.
