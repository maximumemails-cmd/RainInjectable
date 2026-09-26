# License notes

This project derives from two upstream repositories with different terms.

## LionInjectable — GPL-3.0

`upstream/LionInjectable` ships a `LICENSE` file containing the **GNU General
Public License v3.0**. The C++ injector and payload loader in this project are
derived from Lion's native code, so any distribution of the combined work must
comply with GPL-3.0: keep the license text, state changes, and offer
corresponding source. This repository therefore carries the GPL-3.0 text at the
root (`LICENSE`) and this notice.

Changes made vs. Lion V1.0.5 are recorded in `docs/CHANGES-FROM-LION.md`.

## Rain-Anticheat — no declared license

`upstream/Rain-Anticheat` @ `40974c3f` contains **no LICENSE file**. Its README
credits `github.com/femboytatp/meowtils` and `github.com/kireikosasha/MX-Project`
and states the project "was completely AI assisted". Absent an explicit grant,
no license is presumed.

Implications:
- We treat the Rain Java sources as the user's own material to modify for their
  personal use, per the user's instruction to rebuild Rain.
- **Do not redistribute** the combined artifact publicly until the Rain
  copyright/licensing is clarified with its author(s). This is a personal-use
  build.
- Credits from Rain's README/`mcmod.info` are preserved in the runtime jar's
  `mcmod.info`.

## Scope / intent

Rain is a **defensive**, observation-only tool (client-side cheater alerts). It
transmits nothing to servers and confers no gameplay advantage. The injector is
used only to load this defensive tool into the user's own game process on the
user's own machine. Nothing here is designed to bypass or evade any anti-cheat;
Lion's JVMTI/attach-hardening-bypass code was deliberately dropped (see
`docs/CHANGES-FROM-LION.md`).
