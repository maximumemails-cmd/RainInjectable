# Changes from LionInjectable V1.0.5 (GPL-3.0 change statement)

Base: `6a04238f67902472ea2f69e6d683cdfb89aded9f`. Only `injector/src/*`,
`injector/payload/src/dllmain.cpp`, `injector/payload/src/jvm_loader.*` and
`injector/CMakeLists.txt` were taken. Everything else in Lion was dropped.

The Badlion adapter added later is built from Rain detector sources. It uses
the pinned Minecraft 1.8.9 MCP mappings and SpecialSource to remap SRG member
references to Notch names, including inherited members. It schedules checks
through Minecraft's own game-thread task queue. It does not copy Lion's
client JAR, Forge shims, agent attach path, transformers or cheat modules.

## Removed (not vendored)

- `injector/payload/src/agent_attach.{h,cpp}` — in-process `Agent_OnAttach` /
  JVMTI acquisition that works around `-XX:+DisableAttachMechanism`.
- `injector/payload/src/native_bridge.cpp` — `Java_lion_client_NativeBridge_*`
  JNI exports (`attachAgent`, `getLoadedClasses`, `retransform`).
- `client/` — the entire Java side: `lion.client.*` (Agent, LionAgent,
  transformers, Notch remapper, hooks) and `com.lionclient.*` cheat modules.
- `build.bat`, Gradle build, committed `build/` trees, `assets/`.

## Modified

| File | Change |
|---|---|
| `CMakeLists.txt` | project `RainInjectable`; targets `RainInjector` + `rain_payload` (→ `rain-payload.dll`); payload sources reduced to `dllmain.cpp` + `jvm_loader.cpp`; JNI header lookup via `-DJNI_ROOT` / `JAVA_HOME` / Adoptium JDK 8 glob; static CRT (`/MT`) so the payload has no VC++ redistributable dependency |
| `src/*.{h,cpp}` | namespace `lion` → `rain`; all "LionClient"/"LionInjectable" strings → "Rain Injector"/"RainInjector" |
| `src/gui.cpp` | snow animation removed (timer, structs, painting); `doInject` now requires `rain-payload.dll` + `rain-runtime.jar`, refuses positively-identified non-Forge processes, asks for confirmation when the launcher is unclassified (wrapper launchers), refuses 32-bit targets, detects an already-loaded payload; new success/failure messages |
| `src/process_scanner.*` | `LauncherKind::Lunar`/`Badlion` removed with all their detection; candidate exes reduced to `java.exe`/`javaw.exe`; Forge classification requires `net.minecraft.launchwrapper.Launch` plus a Forge marker |
| `src/injector.*` | `writeConfigJson`/`copyAssetTo` removed; `isModuleLoaded(pid, baseName)` added |
| `payload/src/dllmain.cpp` | targets `rain-runtime.jar`/`rain-payload.dll`; opens `rain-payload.log` (upstream never opened its log handle — `payload.log` was never written); logs bootstrap result |
| `payload/src/jvm_loader.*` | loads `first.rain.anticheat.bootstrap.RainBootstrap` and calls `start(String,String)`; attached thread named `Rain-Bootstrap`; `payloadOpenLog()` added; comments rewritten to describe the real flow |

## Unchanged in substance

`CreateRemoteThread(LoadLibraryW)` injection, architecture check, module
verification, Toolhelp32 process scan, PEB command-line read
(`NtQueryInformationProcess`), Win32/GDI+ UI skeleton, file logger,
`jvm.dll` wait loop, `JNI_GetCreatedJavaVMs` → `AttachCurrentThread` →
`URLClassLoader` bootstrap.
