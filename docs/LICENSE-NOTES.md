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

## Rain-Anticheat — no declared public license

`upstream/Rain-Anticheat` @ `40974c3f` contains **no LICENSE file**. Its README
credits `github.com/femboytatp/meowtils` and `github.com/kireikosasha/MX-Project`
and states the project "was completely AI assisted". No general public license
is declared.

The RainInjectable owner reports receiving permission from `@Raindots` on
Discord to publish a modified Rain Anti-Cheat version on GitHub. The supplied
exchange was a request, “Do you mind if I publish a modified version of rain
anti cheat on github?”, answered “Yeh sure”. This is a publication grant as
reported by the owner; the repository does not contain independent verification
of the Discord account's identity or broader license terms. In particular, the
quoted grant does not state whether Rain-derived code may be licensed to others
under GPL-3.0 or another GPL-compatible license. That matters because
RainInjectable combines Rain code with GPL-3.0 LionInjectable code.

- Do not present the root GPL-3.0 text as a general public license granted by
  Rain's copyright holder for the Rain-derived Java sources.
- Clarify GPL-compatible distribution terms before publishing the combined
  project or release artifact.
- Credits from Rain's README/`mcmod.info` are preserved in the runtime jar's
  `mcmod.info`.

## Scope / intent

Rain is a **defensive**, observation-only tool (client-side cheater alerts). It
transmits nothing to servers and confers no gameplay advantage. The injector is
used only to load this defensive tool into the user's own game process on the
user's own machine. Nothing here is designed to bypass or evade any anti-cheat;
Lion's JVMTI/attach-hardening-bypass code was deliberately dropped (see
`docs/CHANGES-FROM-LION.md`).
