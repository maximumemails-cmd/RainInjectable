# RainInjectable 1.3.1-injectable — Windows x64

## Changes since 1.3.0

- Rebuilt the native injector window with a simpler glass-style target, action, progress, status and activity layout.
- Restored original KillAura rotation-window, snap-pattern, movement-fix/lock/sprint and consume checks with current observation safeguards.
- Fixed Screen Flash alert triggering and the Test preview; added Badlion HUD rendering with original HUD delegation.
- Replaced the title-bar drag hint with a styled information icon and a paged module guide; title-bar dragging remains available.
- Restored original detector names in settings and exposed the existing Reach Review switch.

## Included from 1.3.0

- Added a selectable four-second Temporal Analysis mode with bounded player histories.
- Added a conservative AutoCrouch/Eagle edge-pattern review signal requiring repeated sneak transitions at distinct supported block edges.
- Tightened aim and block-use context with competing-swing attribution, separated hurt episodes, and a small supporting player turn baseline.
- Added environmental and network quality gates for movement effects, collisions, liquids, ladders, vehicles, ping changes, tick gaps, and discontinuities.
- Review chat now includes a bounded evidence-confidence percentage based on strength, reliability, repeated episodes, detector diversity, and clean-observation decay. It is not a calibrated cheat probability or a punishment trigger.
- Fixed overlapping Notifications controls and scaled the settings panel for small viewports.

See `RAIN_DETECTION_UPGRADE_REPORT.md` for the implementation, tests, performance bounds, and deliberately deferred detections.

## Distribution

The single-file `RainInjectable.exe` embeds the native payload and both Java runtimes. The ZIP includes the executable, README, GPL-3.0 license, license notes, credits, release notes, and both detection audits. It contains no development cache or private configuration. The corresponding source is the `v1.3.1-injectable` tag.

## Validation and limits

The Java 8 Forge and Badlion runtimes, MSVC x64 Release build, native compatibility and layout tests, bootstrap harness, detector harnesses, static checks, and embedded-resource verification are run for this release. Synthetic tests do not measure real-server false-positive rate or detection recall. Live gameplay has not been revalidated in this release. A passive observer cannot access remote serverbound attacks, clicks, placement packets, or raw mouse input.
