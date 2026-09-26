# Testing

## Static verification (done, repeatable)

| Check | Command | Result (2026-09-26) |
|---|---|---|
| Toolchain present | `scripts\check-environment.ps1` | all required OK; Forge profile + Feather flagged optional/missing |
| Full build | `scripts\build.ps1` | `dist\RainInjector.exe` 267 KB, `dist\rain-payload.dll` ~200 KB, `dist\rain-runtime.jar` 71 KB; 0 warnings (`/W4`) |
| Payload has no JNI exports / no `instrument.dll` | `dumpbin /exports /imports rain-payload.dll` | no exports; imports only `KERNEL32.dll` |
| Both binaries x64 | `dumpbin /headers` | `8664 machine (x64)` twice |
| Jar is Java 8 bytecode | class file major version | 52 |
| `RainBootstrap` references only `java.*` | `javap -v` constant pool | confirmed |
| JNI target signature | `javap -s` | `start(Ljava/lang/String;Ljava/lang/String;)V` |
| `@Mod` retained (jar still works from `mods/`) | `javap -v first.rain.anticheat.Rain` | `Lnet/minecraftforge/fml/common/Mod;` present |
| Bootstrap plumbing end-to-end (fake LaunchWrapper, real SRG MC/Forge jars) | `scripts\test-bootstrap.ps1` | `HARNESS OK (system)`, `HARNESS OK (wrapper)`; re-entry is a no-op |

Not verifiable without a running game: keybind registration, event-bus
delivery, GUI rendering, `addScheduledTask` hand-off, `mcDataDir` config path,
and the actual detections. Those are the live test.

## Live test plan (requires explicit approval — not yet run)

Prerequisites
1. A Forge 1.8.9 profile (Forge `11.15.1.2318`) in the vanilla launcher, **or**
   Feather 1.8.9 (Forge-based). `check-environment.ps1` currently reports neither
   installed. Prism/MultiMC also works (the injector will ask to confirm because
   those launchers hide the main class from the command line).
2. 64-bit Java 8 for the profile (the launcher default `jre-legacy` is fine).
3. `dist\` staged by `scripts\build.ps1`.
4. Windows Defender / AV: `CreateRemoteThread` tools are commonly flagged.
   Expect a SmartScreen prompt for the unsigned exe; add an exclusion for
   `dist\` if the payload gets quarantined. Do **not** disable AV globally.

Procedure
1. Start Minecraft with the Forge 1.8.9 profile; reach the main menu or join a
   world/server (singleplayer or a private server — do not test on a public
   server whose rules forbid client modifications).
2. Run `dist\RainInjector.exe` (no admin needed if Minecraft runs as the same
   user). Click **Refresh**; the `javaw.exe` row should show Launcher = `Forge`,
   Arch = `x64`, JVM = `yes`, LWJGL = `yes`.
3. Select the row, click **INJECT**. Expected: "Injection complete" dialog
   within a few seconds.
4. In-game expected within ~1 s: chat line `Rain 1.0.0-injectable injected. Press
   the GUI key (default RSHIFT) to open settings.`
5. Press **RSHIFT**: the Rain GUI opens with title `Rain ON`, Alerts tab shows
   the `Rain (master)` card plus AutoBlock / LegitScaffold / Killaura.
6. Toggle `Rain (master)` off → title shows `OFF`, chat says `Rain disabled`;
   toggle back on. Close GUI; confirm `.minecraft\config\rain.properties` now
   exists and contains `masterEnabled=true`, `guiKey=54`, `toggleKey=0`.
7. Options → Controls → category **Rain**: two bindings (`Open Rain GUI`,
   `Toggle Rain (master)`). Bind the toggle to a key, press it in-game →
   chat `Rain disabled` / `Rain enabled`. Close GUI once → the key code is
   persisted in `rain.properties`.
8. Click **INJECT** again on the same process → injector shows "Rain is already
   loaded in this process" (no second chat line, no crash).
9. Negative test: start a **vanilla** 1.8.9 profile, Refresh → row shows
   `Vanilla`; INJECT → refused with "Rain requires a Forge 1.8.9 process".
10. Collect logs: `dist\rain-payload.log`, `dist\rain-bootstrap.log`,
    `%TEMP%\RainInjector\` (if present), and the game's `logs\latest.log`
    (look for `[Rain] core started (mode=injected`).

Pass criteria: steps 3–8 behave as described with no game crash or freeze; the
game remains playable; no exceptions containing `first.rain` in `latest.log`.

## Known limitations

- Re-injection after editing the jar requires a game restart (`LoadLibrary` on
  an already-loaded DLL does not re-run `DllMain`; the JVM cannot unload the
  loader).
- The payload jar is only supported through Forge's `LaunchClassLoader`
  (Forge 1.8.9). Fabric/Vanilla/OptiFine-only are refused by design.
- Unsigned binaries; expect AV/SmartScreen friction.
