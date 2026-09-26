# PROJECT_STATUS.md — RainInjectable

Living status file. Updated after every phase. Read this first when resuming.

## Goal

Rebuild Rain Anticheat (a defensive, client-side Forge 1.8.9 mod that flags
suspected cheaters) as an injectable runtime, using the native loader
technology from LionInjectable. Forge 1.8.9 only. Windows x64 only.

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
injector/            C++ (CMake) — RainInjector.exe + rain-payload.dll
  src/               injector exe (Win32 GUI, process scanner, LoadLibrary injector)
  payload/src/       payload DLL (JNI bootstrap only)
runtime/             Java 8 — rain-runtime.jar (Rain core + bootstrap)
  src/               sources (package first.rain.anticheat.*)
  resources/         mcmod.info, assets
  setup.ps1          downloads compile-time deps into runtime/libs (git-ignored)
scripts/             check-environment.ps1, build.ps1, fetch-upstream.ps1
docs/                architecture reviews, license notes, testing plan
dist/                staged release (git-ignored): RainInjector.exe, rain-payload.dll, rain-runtime.jar
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
- [ ] Phase 8 — Live in-game test — **requires explicit user approval — NOT started**

## Blockers / open items

- No Forge 1.8.9 profile installed locally → live test cannot run until one is
  installed (see docs/TESTING.md).
- Rain upstream has **no LICENSE file** — see docs/LICENSE-NOTES.md.
- Visual/GUI changes (master card, title ON/OFF state) are compiled but never
  rendered; unverified until the live test.

## Last verified state (2026-09-26)

- `scripts/check-environment.ps1` → exit 0.
- `scripts/build.ps1` → runtime jar + native Release build, 0 warnings.
- `scripts/test-bootstrap.ps1` → `HARNESS OK (system)`, `HARNESS OK (wrapper)`
  (fake launchwrapper; exercises RainBootstrap classloader discovery, addURL,
  idempotency — does not exercise Minecraft/Forge itself).
- `dist/`: `RainInjector.exe` 752640 B, `rain-payload.dll` 684032 B,
  `rain-runtime.jar` 71490 B (29 classes), `LICENSE`, `LICENSE-NOTES.md`.
- dumpbin: both native binaries x64; payload has no exports and imports only
  KERNEL32 (static CRT).
- javap: `RainBootstrap` references only `java.*`; `@Mod` retained on `Rain`
  so the jar also works from a `mods/` folder.
- Not verified: anything in a running game (injection, Forge hook, GUI, alerts).
