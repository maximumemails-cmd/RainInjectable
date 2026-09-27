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
anti cheat on github?”, answered “Yeh sure”. In a follow-up exchange, the
owner asked, “May I distribute modified Rain Anti-Cheat source as part of
RainInjectable under GPL-3.0, so recipients may use, modify, and redistribute
it under GPL-3.0?” The answer was, “Yes that's also fine.”

On the basis of those owner-supplied permissions, Rain-derived Java sources in
this project are distributed under GPL-3.0 alongside the Lion-derived native
sources. The repository does not independently verify the Discord account's
identity or rights in any material credited by the original Rain README.
- Credits from Rain's README/`mcmod.info` are preserved in the runtime jar's
  `mcmod.info`.

## Scope / intent

Rain is a **defensive**, observation-only tool (client-side cheater alerts). It
transmits nothing to servers and confers no gameplay advantage. The injector is
used only to load this defensive tool into the user's own game process on the
user's own machine. Nothing here is designed to bypass or evade any anti-cheat;
Lion's JVMTI/attach-hardening-bypass code was deliberately dropped (see
`docs/CHANGES-FROM-LION.md`).
