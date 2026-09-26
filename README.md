# RainInjectable

Rain Anticheat (defensive, observation-only Forge 1.8.9 mod that flags suspected
cheaters in local chat) rebuilt as an injectable runtime, using the native
loader from LionInjectable V1.0.5. Windows x64 + Forge 1.8.9 only.

Rain observes other players and alerts the local user. It sends nothing to the
server, changes no gameplay, and contains no anti-cheat evasion code.

## Layout

- `injector/` — C++/CMake: `RainInjector.exe` (Win32 GUI + LoadLibrary injector) and `rain-payload.dll` (JNI bootstrap only)
- `runtime/` — Java 8: `rain-runtime.jar` (Rain core + `RainBootstrap`); also loadable from a `mods/` folder
- `scripts/` — `check-environment.ps1`, `build.ps1`, `test-bootstrap.ps1`, `fetch-upstream.ps1`
- `docs/` — architecture reviews, `CHANGES-FROM-LION.md`, `TESTING.md`, `LICENSE-NOTES.md`, `UPSTREAM.md`
- `PROJECT_STATUS.md` — living status; read first when resuming

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-environment.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-bootstrap.ps1
```

Requires JDK 8, CMake, VS 2022 Build Tools (MSVC x64). Output is staged in `dist/`.

## Usage

Keep `RainInjector.exe`, `rain-payload.dll` and `rain-runtime.jar` in the same
folder. Start Forge 1.8.9, reach the main menu, run the injector and pick the
Minecraft process. Right Shift opens the Rain GUI (master enable/disable card,
alert/flash/nametag settings). Config: `.minecraft\config\rain.properties`.
Logs: `rain-payload.log`, `rain-bootstrap.log` beside the exe.

See `docs/TESTING.md` before any live test.

## License

Loader code derives from LionInjectable (GPL-3.0, see `LICENSE`). Rain upstream
has no license; this is a personal-use build — see `docs/LICENSE-NOTES.md`.
