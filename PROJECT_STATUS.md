# PROJECT_STATUS.md — RainInjectable

Living status file. Updated after every phase. Read this first when resuming.

## Goal

Rebuild Rain Anticheat (a defensive, client-side Minecraft 1.8.9 detector that
flags suspected cheaters) as an injectable runtime, using the native loader
technology from LionInjectable. Forge and Badlion Client 1.8.9; Windows x64.

Rain is a **defensive** tool: it observes other players and alerts the local
user. It sends nothing to the server, alters no gameplay, and grants no
advantage. Nothing in this project bypasses or hides from any anti-cheat.

## Workspace

- Location: `%USERPROFILE%\Dev\RainInjectable` (Downloads was deliberately not
  used as a working directory; the VS Code folder `Downloads\Very cool project`
  is empty and untouched).
- Git: `main` branch, repo-local identity `RainInjectable Dev <dev@localhost>`.
- Upstream clones live in `upstream/` (git-ignored, re-fetchable via
  `scripts/fetch-upstream.ps1`). Pinned SHAs are in `docs/UPSTREAM.md`.

## Layout

```
injector/            C++ (CMake) — RainInjectable.exe + rain-payload.dll
  src/               injector exe (Win32 GUI, process scanner, LoadLibrary injector)
  payload/src/       payload DLL (JNI bootstrap only)
runtime/             Java 8 — rain-runtime.jar (Rain core + bootstrap)
  src/               sources (package first.rain.anticheat.*)
  resources/         mcmod.info, assets
  setup.ps1          downloads compile-time deps into runtime/libs (git-ignored)
scripts/             check-environment.ps1, build.ps1, fetch-upstream.ps1
docs/                architecture reviews, license notes, testing plan
dist/                separate build artifacts (git-ignored)
releases/            single-file RainInjectable.exe and ZIP (git-ignored)
```

## Toolchain audit (2026-09-26)

| Tool | Status | Notes |
|---|---|---|
| Windows 11 x64 (10.0.26200) | OK | |
| Git 2.x | OK | on PATH |
| VS Code + `code` CLI | OK | |
| JDK 8 (Temurin 8.0.504.1) | OK | `C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot` — used for `javac -target 1.8` and `jni.h` |
| JDK 21 (Temurin) | OK | default `java` on PATH; not needed by this build |
| CMake 4.4.3 | OK | on PATH |
| Ninja 1.13.2 | OK | on PATH |
| VS 2022 Build Tools, MSVC 14.44, `vcvars64.bat` | OK | `C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools` |
| Windows 10 SDK | OK | `C:\Program Files (x86)\Windows Kits\10` |
| PowerShell 5.1 | OK | |
| Gradle | MISSING | **not required** — Rain is compiled with plain `javac`; Lion's Gradle client build is dropped |
| Forge 1.8.9 profile in `.minecraft` | MISSING | required only for the **live test**; `versions/1.8.9` exists (vanilla) but no Forge profile/libraries. Feather/Lunar/Badlion/Modrinth launchers present. |

## Phase log

- [x] Phase 0 — toolchain audit, upstream clones, workspace + git init (`2a79814`)
- [x] Phase 1 — architecture review docs (Lion, Rain) (`e6cd568`)
- [x] Phase 2 — baseline builds of upstream: Rain jar via javac/JDK 8, Lion native via CMake/VS17 (`35939a4`)
- [x] Phase 3 — Rain refactor: `RainCore`, master enable/disable, toggle keybind, `rain.properties` config, GUI master card (`0e1309f`)
- [x] Phase 4 — Lion loader stripped to native-only (no JVMTI/agent_attach/native_bridge/ASM/cheat code), rebranded `RainInjector` (`03105f4`, `7c01e65`)
- [x] Phase 5 — Payload loads `rain-runtime.jar` via `RainBootstrap.start(jar, dll)` (`7c01e65`)
- [x] Phase 6 — `scripts/build.ps1`, `scripts/check-environment.ps1`, `dist/` staging (`7c01e65`)
- [x] Phase 7 — Static verification, bootstrap harness, docs (`89f2ea8`, `3413cf9`, this commit)
- [x] Single-file build — DLL and Forge/Badlion JARs embedded in `releases/1.1.0-injectable/RainInjectable.exe`; extracted to a versioned Local AppData folder on injection
- [x] Live Badlion test — native load, JNI bootstrap, in-world detector ticks and Right Shift GUI opens verified
- [ ] Live Forge test — still pending
- [x] Injection completion now waits for the Java bootstrap result; compatibility decisions and Windows utility UI rebuilt

## Blockers / open items

- No Forge 1.8.9 profile installed locally; Forge live injection was not tested.
- Badlion detector checks run live and use local chat for alerts. A real alert
  was not triggered in this test. The GUI opens from Right Shift; Flash and
  Nametag overlays still need a Badlion render hook.
- Rain upstream has **no LICENSE file** — see docs/LICENSE-NOTES.md.
- Public GitHub release is pending a repository remote, valid GitHub CLI
  authentication, and confirmation of Rain redistribution rights.
- The Badlion GUI opened from Right Shift and closed between opens. The master
  control changed and persisted across a close and reopen. The final build
  hides the unavailable overlay controls.

## Last verified state (2026-09-27)

- The supplied Lion binaries match the pinned upstream build byte-for-byte.
  Rain includes their native LoadLibrary/JNI path and now has a separate
  Notch-mapped Badlion detector adapter. The UI enforces its compatibility
  decision, clears stale bootstrap status, waits through Java startup, and no
  longer claims to eject Rain. Lunar remains unsupported.

- Version 1.1.0-injectable implementation: parent-aware Minecraft JVM discovery,
  typed compatibility stages, redacted console diagnostics, four-line payload
  status, and a rebuilt compact Win32 interface. A clean MSVC x64 Release build,
  native compatibility and DPI layout tests, Java bootstrap harness, detector
  checks, embedded resource verification, and `git diff --check` passed.
- Live Badlion check: Java 17.0.13 x64, Minecraft 1.8.9, no Forge, Notch class
  names. Native DLL and JNI loaded. In restarted PID 27452, Rain's status was
  `Complete`, game-thread heartbeats and processed detector ticks increased,
  51 eligible players were observed, `lastError` was empty, and the game stayed
  responsive. This does not prove alert accuracy against cheaters.

- Clean MSVC x64 Release build of the native EXE/DLL and JDK 8 runtime JAR:
  `scripts/build.ps1` passed with no compiler warnings.
- Native compatibility CTest, synthetic system/wrapper/Unicode/failure bootstrap
  harness, 345 detector assertions, detector static checks, `git diff --check`,
  and `scripts/verify-release.ps1` passed.
- `releases/` contains the single-file EXE, ZIP bundle and release notes.
  Embedded DLL and both JARs match the build outputs byte-for-byte; version
  markers match.
- Forge event delivery, in-game Forge Rain GUI and visual desktop rendering
  have not been verified.
