# RainInjectable

Rain Anti-Cheat (defensive, observation-only Minecraft 1.8.9 detector that
shows reviewable local evidence) rebuilt as an injectable runtime, using the
native loader from LionInjectable V1.0.5. Windows x64; Forge 1.8.9 and Badlion
Client 1.8.9 have separate Java runtimes.

Rain observes other players and shows review candidates to the local user. It sends nothing to the
server, changes no gameplay, and contains no anti-cheat evasion code.
Use it only with games and environments where you have permission to do so.

## Layout

- `injector/` — C++/CMake: `RainInjectable.exe` (Win32 GUI + LoadLibrary injector) and `rain-payload.dll` (JNI bootstrap only)
- `runtime/` — Java 8: `rain-runtime.jar` (Forge core + bootstrap) and `rain-badlion.jar` (obfuscated 1.8.9 detector adapter)
- `scripts/` — `check-environment.ps1`, `build.ps1`, `test-bootstrap.ps1`, `fetch-upstream.ps1`
- `docs/` — architecture reviews, `CHANGES-FROM-LION.md`, `TESTING.md`, `LICENSE-NOTES.md`, `UPSTREAM.md`
- `RAIN_NEXT_GEN_DETECTION_RESEARCH.md`, `NEXT_GEN_IMPLEMENTATION_SUMMARY.md`, and `RAIN_DETECTION_UPGRADE_REPORT.md` — observer limits, implementation audits, and detection policy

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-environment.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-bootstrap.ps1
ctest --test-dir build/native -C Release --output-on-failure
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-release.ps1
```

Requires JDK 8, CMake, VS 2022 Build Tools (MSVC x64). The Badlion build
downloads pinned Minecraft 1.8.9 mappings and SpecialSource into ignored
`runtime/libs/`. The single-file
build is `releases/1.3.1-injectable/RainInjectable.exe`, with a ZIP bundle beside it. The DLL and
both JARs are embedded in the EXE; separate build artifacts in `dist/` are for
inspection and testing.

## Usage

For a ready-to-run Windows x64 build, download
`RainInjectable-Windows-x64.zip` from the
[GitHub Releases page](https://github.com/maximumemails-cmd/RainInjectable/releases),
extract it, and run `RainInjectable.exe`. The ZIP includes the license and
attribution notes. The standalone EXE is also available on the release page.

Start Forge or Badlion Client with Minecraft 1.8.9, reach the main menu, run
`RainInjectable.exe`, and select the game process.
Compatibility shows staged evidence and the exact unsupported reason for the
selected target. Badlion initially shows **Needs verification** because the
in-process adapter checks its game classes and scheduling. Lunar, Fabric,
vanilla, other versions, and 32-bit Java are not offered for injection. The
EXE extracts its embedded DLL and JARs into a
versioned folder under `%LOCALAPPDATA%\RainInjectable`. The Logs page reports
the actual bootstrap result. Right Shift opens Rain settings on Forge and on
the Badlion 1.8.9 adapter. Badlion polls the configured GUI and master toggle
keys on the game thread, runs detector checks, and reports review candidates in local
chat. Both runtimes can export bounded, locally aliased evidence from the Notifications tab.
Screen Flash now runs on Forge and the Badlion HUD, including the Notifications
Test button. The information icon opens a paged guide to every detection module.
The original KillAura rotation, movement-fix and consume checks are restored
alongside the newer checks. Badlion nametags remain unsupported, and the review
policy does not create public marks. The new HUD hook still needs live gameplay verification.
Config: `.minecraft\config\rain.properties`.

Temporal Analysis keeps a bounded four-second player history. Edge-crouch and
combat reviews require repeated, quality-gated evidence; chat shows an evidence
confidence percentage, not a calibrated probability of cheating. Rain does not
identify subtle cheating from packet/click data it cannot observe. See
[the upgrade report](RAIN_DETECTION_UPGRADE_REPORT.md) and
[the implementation audit](NEXT_GEN_IMPLEMENTATION_SUMMARY.md) for protections
and remaining limits.

Live Badlion testing confirmed DLL loading, Java bootstrap, game-thread
heartbeats, GUI class initialization, and detector ticks in a server with other players. Live Forge
injection has not been tested; see `docs/TESTING.md`.

## License

Loader code derives from LionInjectable (GPL-3.0, see `LICENSE`). Rain upstream
has no declared public license; the RainInjectable owner reports explicit
permission from its author to distribute this modified Rain source as part of
RainInjectable under GPL-3.0. See [license notes](docs/LICENSE-NOTES.md).

## Credits and acknowledgements

Rain Anti-Cheat supplied the detection foundation and LionInjectable supplied
the native injector/JVM architecture. Their roles, original credits, research
references, and AI-assisted development are documented in
[CREDITS.md](CREDITS.md).
