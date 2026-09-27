# RainInjectable 1.1.0-injectable — local Windows x64 release

## Changes

- Discover the game JVM through its launcher parent and report separate
  discovery, JVM, architecture, Minecraft, framework, attach, payload,
  bootstrap and Rain runtime stages.
- Show a focused Home view, detailed Compatibility view, useful Logs and the
  existing automatic detection setting in a rebuilt Win32 interface.
- Use a restrained DWM-backed chrome area and shared layered glass treatment
  for interactive controls, with a solid fallback when transparency is
  unavailable.
- Pass payload failure codes and next actions through the status file. Redact
  authentication arguments from UI and console diagnostics.
- Refuse injection when the selected JVM has no Rain bootstrap strategy.
  Clear stale status before injection and wait for the full game-thread startup
  window before reporting a timeout. Remove the misleading native eject action;
  Rain stays registered in Java until the game exits.
- Add a separate Badlion Client 1.8.9 runtime JAR. It remaps Rain's detector
  and settings GUI references from SRG to obfuscated Minecraft names, schedules
  checks and polls Right Shift on Badlion's game thread. The Badlion screen
  shows detector and debug controls; its Forge-only overlay tabs are hidden.
  The JAR does not include Lion cheat classes.

## Badlion finding

The running Badlion Minecraft process was Java 17.0.13 x64, Minecraft 1.8.9.
It had no Forge event bus and used obfuscated Minecraft classes. The native
Lion-derived DLL loading path succeeded. A dedicated Rain adapter then started
without Forge. In a restarted server session, its local status showed 51
eligible players, increasing detector ticks, no runtime error, and a responsive
game. A later session confirmed GUI class initialization and two Right Shift
opens in the live game, while detector ticks continued without errors. This
validates startup, keyboard handling and the observation loop in those sessions. Alert accuracy and
compatibility with other Badlion builds are not established by this test.

## Validation and limits

The MSVC x64 Release build, Java 8 runtime builds, native compatibility and
layout tests, synthetic bootstrap harness, detector harness and static checks,
and embedded resource verification passed. A live Forge instance was
unavailable, and automated desktop visual testing was unavailable. Badlion's
adapter runs detectors, local chat alerts, and the settings GUI. Flash and
Nametag overlays still need a Badlion render hook.

The supplied Lion executable, DLL and JAR match the pinned upstream build.
Rain uses the native DLL loading path and a separate Java detector runtime for
Badlion. Lunar support is not implemented or verified in this release.

The owner reports permission to publish a modified Rain version on GitHub.
GPL-compatible terms for distributing the combined Rain/Lion work remain
unconfirmed. Public release also requires a Git remote and working GitHub
authentication.
