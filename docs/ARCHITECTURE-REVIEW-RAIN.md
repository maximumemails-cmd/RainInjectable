# Architecture review — Rain-Anticheat (main @ 40974c3f)

Source: `upstream/Rain-Anticheat`. License: **none declared** (see LICENSE-NOTES.md).
README credits meowtils and MX-Project; states "Compatible with FORGE/FEATHER only. Keybind is RSHIFT".

## What it is

A ~2.8 k-line client-side Forge 1.8.9 mod that watches *other* players in the
local world and prints a chat alert (plus optional screen flash and a Tab-list
marker) when their behaviour matches a cheat heuristic. It is observational
only — verified by grep: no `sendPacket`/`addToSendQueue`, no input synthesis,
no rotation or motion writes.

## Build

Plain `javac`, no Gradle/ForgeGradle:

- `setup.ps1` downloads vanilla 1.8.9, Forge `11.15.1.2318` universal, MCP
  `mcp-1.8.9-srg.zip`, SpecialSource 1.11.4 and LWJGL 2.9.4, then remaps
  vanilla and Forge-universal to **SRG names**.
- `build.ps1` compiles `src/` against the SRG jars with `--release 8` (or
  `-source/-target 1.8` on a real JDK 8), copies `resources/`, and jars
  `rain AC.jar`.

Because the sources are written directly in SRG names (`func_71410_x`,
`field_71439_g`, …) no reobfuscation step exists or is needed: the jar is
already in the shape FML's runtime deobfuscator produces for game classes.
This is the property that makes injection through `LaunchClassLoader` work
without any remapper.

## Package map (`first.rain.anticheat`)

| Class | Role | Forge coupling |
|---|---|---|
| `Rain` | `@Mod(modid="rain")`; `init(FMLInitializationEvent)` registers keybind + 3 event listeners; `onClientTick` drives per-player checks; `onWorldUnload` clears state; static `addMessage()` prints a **client-side** chat line | `@Mod`, `MinecraftForge.EVENT_BUS`, `TickEvent`, `WorldEvent` |
| `config.cfg` | Static settings holder (`cfg.v.*`) persisted to `config/rain.properties`; loaded in a static block, saved on GUI close | none |
| `gui.ClickGui` / `ClickGuiKeybind` | `GuiScreen` with Alerts/Notifications/Nametags tabs; keybind `KEY_RSHIFT` in category "Rain" opens it | `GuiScreen`, `KeyBinding`, `ClientRegistry`, `InputEvent`, LWJGL `Keyboard` |
| `gui.ModuleCard` / `FlashSettingsCard` / `NametagSettingsCard` | Toggle/slider cards writing to `cfg.v` | `FontRenderer`, `PositionedSoundRecord` |
| `util.RenderUtil` | Rounded-rect/GL helpers | LWJGL `GL11`, `Gui` |
| `util.anticheat.AntiCheatData` | Owns `PlayerData` + 3 checks; `anticheatCheck(player)` fans out; retain/forget/clear lifecycle | `EntityPlayer` |
| `util.anticheat.PlayerData` | Per-player rolling motion/rotation history | `EntityPlayer` |
| `util.anticheat.PlayerEligibility` | Gate: real, connected, non-spectator, non-self players (guards against server bot `EntityPlayer`s) | `NetHandlerPlayClient`, `NetworkPlayerInfo`, `GameType` |
| `util.anticheat.AlertManager` | First flag per player → one chat line + flash; marks player; later flags silent | `EntityPlayer`, `EnumChatFormatting` |
| `util.anticheat.FlashNotification` | Full-screen flash on `RenderGameOverlayEvent.Post` + `note.pling` | `RenderGameOverlayEvent`, `Gui`, `ScaledResolution` |
| `util.anticheat.NametagOverlayRenderer` | Recolours marked players in the Tab list on `RenderGameOverlayEvent.Post(PLAYER_LIST)` | Tab overlay, scoreboard, reflection |
| `util.anticheat.checks.AutoBlockCheck` | Swing while sword-blocking | `EntityPlayer`, items |
| `util.anticheat.checks.LegitScaffoldCheck` | Robotic crouch-bridge rhythm | `EntityPlayer`, `BlockPos`, `ItemBlock` |
| `util.anticheat.checks.KillauraCheck` (810 l) | Silent/snap aim, robotic rotations, hitting through eating, geometry checks | `EntityPlayer`, math |

## Detection model

`Rain.onClientTick` (phase END) iterates `world.playerEntities`, gates each with
`PlayerEligibility.shouldCheckPlayer` (never the local player), and calls
`AntiCheatData.anticheatCheck`. Each check tracks rolling per-player state and,
on threshold, calls `AlertManager.flag(player, type, vl)`. The first flag emits
one chat line, triggers the flash, and marks the player so the Tab list
recolours them. State is torn down on ineligibility, `retainPlayers`, and world
unload. All output is local; nothing is transmitted.

## Compatibility notes / issues

- README says "FORGE/FEATHER only". Feather runs on Forge for 1.8.9, so a Forge
  event-bus mod loads there. Our injector targets Forge processes; Feather is a
  Forge profile and is covered without special casing.
- The upstream `Rain.java` header comment invites replacing it with an
  "original" `Rain.java`; the shipped one is self-consistent and is what we
  build on.
- No `@SideOnly(CLIENT)` annotations, but the mod is client-only by content;
  irrelevant for a client injection scenario.

## What changes for the injectable build (design)

Rain today is a *mods-folder* Forge mod: FML calls `@Mod.init`. When injected
at runtime the `@Mod` lifecycle has already run, so **something must call the
equivalent of `init()` after the jar is added to the classpath**. The plan:

1. Extract the current `Rain.init()` body into a `RainCore.start()` that
   registers the keybind and the three event-bus listeners exactly once
   (idempotent guard), independent of the `@Mod` annotation.
2. Keep `@Mod` so the same jar still works as a normal mod (dual-mode), but
   have the injected bootstrap (`first.rain.anticheat.bootstrap.RainBootstrap`,
   called from the Java `Agent`) invoke `RainCore.start()` directly.
3. Add a **master enable/disable toggle** (`cfg.v.masterEnabled`) gating all
   detection and rendering, surfaced in the ClickGUI and honoured by
   `onClientTick`, the renderers, and `AlertManager`.
4. Keep RSHIFT keybind and `rain.properties` config.
