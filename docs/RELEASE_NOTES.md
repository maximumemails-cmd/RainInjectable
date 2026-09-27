# RainInjectable 1.2.0-injectable — Windows x64

## Detection and evidence

- Forge and Badlion 1.8.9 now collect a complete tick-snapshot batch before running checks, with bounded per-player history and continuity, ping, teleport, and observer tick-quality gates.
- Gross reach candidates use the minimum attacker-to-victim-box distance across recent positions, a broad observer allowance, isolated combat attribution, and repeated independent episodes. Subtle reach is intentionally left unclassified.
- Legacy aim, AutoBlock, and bridging-rhythm checks now contribute weak, reviewable evidence. They do not issue an unvalidated red mark or describe a player as confirmed cheating.
- An ordinal evidence ledger caps checks that share inferred attack data, accumulates separated episodes, and decays only during usable observation. Its index is not a cheat probability.
- The Notifications tab can export bounded local JSONL evidence, including reasons for abstention and historical samples that replay the reach calculation. UUIDs are replaced with aliases for each export.
- New thresholds are documented in `NEXT_GEN_IMPLEMENTATION_SUMMARY.md`; `README.md` links the research and implementation audit.

## Distribution

The single-file `RainInjectable.exe` embeds the native payload and both Java runtimes. The ZIP includes the executable, README, GPL-3.0 license, license notes, credits, release notes and the implementation audit. It contains no development cache or private configuration. The corresponding source is the GitHub release tag.

## Validation and limits

The Java 8 Forge and Badlion runtimes, MSVC x64 Release build, native compatibility and layout tests, bootstrap harness, detector/observation/evidence harnesses, static checks and embedded resource verification are run for this release. Synthetic tests do not measure real-server false-positive rate or detection recall. Live Forge and updated Badlion gameplay behavior have not been revalidated in this release. A passive observer cannot access remote serverbound attacks, clicks, placement packets or raw mouse input. No high-confidence public claim is enabled without controlled observer/server captures and a representative legitimate-player validation set.
