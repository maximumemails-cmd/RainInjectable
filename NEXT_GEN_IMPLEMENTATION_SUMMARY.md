# Next generation detection implementation (1.2.0-injectable)

This document records the implementation against `RAIN_NEXT_GEN_DETECTION_RESEARCH.md`. Rain is a passive Minecraft 1.8.9 observer. Its relayed, interpolated entity stream cannot identify a remote player's serverbound attacks, clicks, placements, mouse input, or exact server hit decision. **Review is an ordinal evidence state, not a cheat probability or a confirmed accusation.** No automatic bans or red marks are issued by the new policy.

## Data flow

Both Forge and Badlion collect one complete player batch per world tick, including the local player as a possible alternate actor. `ObservationEngine` holds at most eight snapshots per UUID and at most 64 recent hurt candidates and 64 review measurements. A snapshot contains position, eye/box dimensions, relayed yaw/pitch, swing animation state, hurt timer, tab-list RTT proxy, ride state, tick, and a monotonic time for review events. It is explicitly a **tick-interpolated entity** source.

An onset in the victim's hurt timer starts a candidate. The engine records why it abstains: poor victim/actor history or ping, absent relayed swing, multiple nearby actors, nonfinite geometry, or reuse of the same fight. A plausible legal reconstruction is recorded separately from a gross distance candidate. This attribution is still uncertain: environmental damage, projectiles, custom server behavior and unobserved actors remain possible.

For a gross reach candidate, both players must be reported in Survival mode and have five continuous, nearly stationary snapshots, known low tab RTT proxy, and no nearby alternate actor. The engine computes the **minimum** eye-to-victim-box distance over all pairs of the recent attacker and victim snapshots. That favors every plausible legal position pair. It requires the distance to exceed the 3.0 block vanilla assumption plus a 1.5 block observer allowance and a further 0.5 block residual. These deliberately broad defaults are only a review research envelope; they are **not empirically calibrated** and do not prove server reach rules. High ping, gaps, low observer tick rate, teleports and crowds abstain. Two separated episodes are required before the geometry group alone reaches review.

`EvidenceLedger` caps contributions by dependency group within a 20-tick fight, so Killaura and AutoBlock cannot corroborate each other merely by inheriting the same inferred attack. It holds an ordinal index of at most 12 units, requires at least two separated contributing episodes and five units for review, and decays one unit per 40 usable observed ticks. Unobserved time does not clear evidence. Legacy aim/block and crouch rhythm checks each contribute one weak unit and no longer issue a first-flag-wins mark. The red nametag/flash mechanism remains in source for compatibility but receives no unvalidated mark from these checks. UI text says a pattern needs review.

The Notifications tab offers a **user-triggered local export** into `.minecraft/config/rain-evidence-<time>.jsonl`. It contains bounded candidate outcomes, evidence measurements, historical snapshots sufficient to replay the distance calculation, and ledger facts. UUIDs become session-local aliases. Positions still describe gameplay and should be shared only intentionally. No export runs during ordinary gameplay.

## Configuration

`.minecraft/config/rain.properties` retains the legacy check switches and adds:

| Key | Default | Meaning |
|---|---:|---|
| `reviewGrossReach` | `true` | Enable gross reach review candidates. |
| `maxObserverPingMs` | `150` | Maximum tab-list RTT proxy for analyzed players. Unknown or higher RTT abstains. |
| `reachObserverSlack` | `1.5` | Extra blocks allowed for passive observer uncertainty; lower values clamp to 1.5. |
| `reachGrossResidual` | `0.5` | Additional excess required after the legal reach plus slack; lower values clamp to 0.5. |

These values are engineering safety bounds, not learned false-positive cutoffs. Server plugins or translated protocols can invalidate the vanilla reach assumption. This release therefore never promotes a reach candidate to a confirmed/public flag.

## Research recommendation audit

| Research topic | Result and technical reason |
|---|---|
| Observation provenance, bounded history, quality/missingness, replay | Implemented for the tick source. Candidate outcomes preserve abstention reasons; gross reach reviews retain source history and export. No fabricated packet timestamps. |
| Attack attribution and crowd alternatives | Implemented conservatively for gross reach; a nearby alternate actor forces abstention. Legacy Killaura no longer accepts crowded target sets for its combat gate. Hurt cause remains unknown. |
| Conservative physical reach envelope | Implemented as minimum over both position histories with broad slack for stationary, isolated cases. This is a review candidate, not a line-of-sight or definitive hit verdict; remote look and server rules are too uncertain for a public claim. Subtle 3.1–3.3 reach remains unidentifiable. |
| Confidence accumulation, dependency grouping, clean exposure decay, staged result | Implemented as ordinal watch/review ledger. It is intentionally not labeled a probability. No high-confidence tier is shown without a measured false-positive rate. |
| Existing aim, AutoBlock and scaffold checks | Preserved as weak research signals; public accusation is demoted. Aim snap/tracking are correlated presentation features, block overlap shares inferred attack identity, and crouch rhythm has no attributed placement. These are not independent proof. |
| Per-player statistical aim baselines and multi-target geometry | Deferred as alerting detectors. Tick-interpolated byte-angle rotations, target ambiguity and lack of matched legitimate data make a numerical baseline or switch limit unreliable. Current bounded histories and export support later controlled evaluation. |
| Entity-local lag/knockback/NoSlow/placement/timer/flight | Deferred as alerting detectors. No verified packet update coverage, impulse, placement actor, terrain/version rule profile, or server policy is available in both runtimes. Single-player lag and skilled bridging have known legitimate lookalikes. Candidate quality gates prevent these conditions from strengthening current evidence. |
| Incoming passive packet tap | Evaluated architecturally and deferred. Forge and the injected Badlion adapter do not share a proven read-only packet interception point; adding a hook without lifecycle and thread-safety tests would risk the game network path. An incoming tap would still not reveal the suspect's serverbound stream. |
| Clicker, GCD, WTap, packet Disabler, ping spoof, BackTrack, partial Velocity, silent camera claims | Intentionally not implemented as named detectors. The required source signals are absent or non-identifiable in this observer, as specified in research §9.8. |
| Dataset and ML | Deferred. There is no consent-based, grouped, labeled observer corpus or measured per-session false-positive rate. A model or claimed percentage would be unjustified. The bounded export supplies the correct deployed-stream schema starting point. |

## Validation and remaining limits

Synthetic harnesses exercise legacy checks, gross and subtle distance, high ping, crowded combat, moving combat, gaps, repeated episodes, reset, dependency caps, clean exposure decay, alias export and deterministic distance replay. Build verification covers both Java runtimes, native release resources and the distributor ZIP. These tests do **not** establish real-world detection accuracy. Controlled paired observer/server capture, a false-positive gauntlet with skilled players and unstable networks, and shadow deployment remain necessary before any high-confidence public label is justified.

The passive client cannot reliably detect all modern cheats. In particular, ordinary aim assistance, exact CPS, partial velocity and slight reach often yield observations also possible during legitimate play. Rain now exposes that uncertainty instead of escalating those cases into a confirmed mark.
