# Credits and acknowledgements

## RainInjectable

RainInjectable combines the Rain anti-cheat detection foundation with an
injectable native/JVM runtime and subsequent project development. The local
RainInjectable commits use the shared `RainInjectable Dev <dev@localhost>`
identity, which does not establish an individual human author.

## Core foundations

- **[Rain Anti-Cheat](https://github.com/JasonWangFTW/Rain-Anticheat)** supplied
  the original Java anti-cheat and detection foundation. Its Git history
  identifies `JasonWangFTW` as a committer. Its README credits
  [meowtils](https://github.com/femboytatp/meowtils) and
  [MX-Project](https://github.com/kireikosasha/MX-Project); its `mcmod.info`
  lists “Meowtils & MXproject” as authors. We preserve those upstream credits
  without assigning individual RainInjectable changes to them.
- **[LionInjectable](https://github.com/LionClientINC/LionInjectable)** supplied
  the baseline injector, native payload, and JVM loading architecture. Its
  history credits the `LionClient` / `LionClientINC` account. RainInjectable
  adapted selected V1.0.5 native sources for the path
  `RainInjectable.exe → rain-payload.dll → JVM → Rain runtime`; see
  [the change statement](docs/CHANGES-FROM-LION.md). LionInjectable's
  developers are not represented as RainInjectable developers or endorsers.

The pinned upstream revisions are in [docs/UPSTREAM.md](docs/UPSTREAM.md).
The original repositories retain their own contributor histories.

## Detection research references

The following projects were referenced to study cheat behavior or anti-cheat
approaches. They are research references, not claimed source-code dependencies,
RainInjectable authors, or endorsers.

- [Raven](https://github.com/K-ov/Raven) and
  [NightX Client](https://github.com/Aspw-w/NightX-Client): client module
  behavior and observable effects.
- [Grim Anticheat](https://github.com/GrimAnticheat/Grim) and
  [Iustitia](https://github.com/ThoriaDevelopment/Iustitia): detection
  architecture, player state, and false-positive considerations.
- [Lumo AI Detector](https://github.com/isLumo/LumoAiDetector),
  [Shard](https://github.com/KaelusAI/Shard), and
  [AimNet Mouse Dynamics](https://github.com/templateprotection/AimNet-Mouse-Dynamics):
  research into evidence fusion, timing, confidence, and aim or mouse dynamics.

These acknowledgements do not assert direct code reuse. Further research
context and limitations are recorded in
[RAIN_NEXT_GEN_DETECTION_RESEARCH.md](RAIN_NEXT_GEN_DETECTION_RESEARCH.md).

## AI-assisted development

- **OpenAI Codex** assisted extensively with repository analysis,
  implementation, debugging, builds, native/JVM integration, compatibility,
  tests, documentation, detection research, and review.
- **Anthropic Claude** assisted with architecture analysis, implementation,
  debugging, research, code reasoning, and review of approaches.
- **OpenAI ChatGPT** assisted with planning, research, prompt development,
  architecture discussion, debugging, and detection-system design.

These are development tools, not human contributors or Git commit authors.
Their inclusion implies no endorsement by their providers and does not assign
specific lines of code to particular models.

## Licenses and notices

LionInjectable is licensed under **GPL-3.0**. Its license text is preserved in
the root [LICENSE](LICENSE), and changes to its native sources are recorded in
[docs/CHANGES-FROM-LION.md](docs/CHANGES-FROM-LION.md).

The pinned Rain Anti-Cheat repository has **no declared license**. Its public
availability alone does not grant redistribution rights. The root GPL text
does not resolve rights in Rain-derived Java sources. See
[docs/LICENSE-NOTES.md](docs/LICENSE-NOTES.md) for this unresolved issue.

Minecraft, Forge, MCP mappings, LWJGL, and SpecialSource are obtained or used
at build time; their downloaded artifacts are not committed here. Their own
terms apply independently.
