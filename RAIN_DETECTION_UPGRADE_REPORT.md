# Rain detection upgrade

## Changes

- **Temporal Analysis** is a persistent Alerts-tab toggle. Enabled mode retains at most 80 tick snapshots (about four seconds) per visible player; disabled mode retains the original eight. The new edge-pattern analyzer shares the sampled fields rather than querying entities again. State is cleared on world change, disable, discontinuity, and player removal.
- **AutoCrouch/Eagle candidate:** repeated sneak onsets must occur at seven distinct, full-block supported edges within the short window, while moving with blocks held. Similar edge distances across the sequence add evidence. It is a review hypothesis, never a confirmed cheat label. The older bridge-rhythm check now requires measured edge context; both checks share one dependency group.
- **Combat:** inferred hits for aim now reject competing nearby relayed swings. Three hurt-correlated events must be separated by at least six ticks. A clean non-swing turn median from the short history can add a small amount of *supporting* aim evidence. Gross reach keeps its conservative best-case position envelope and now weighs the observed residual and RTT quality.
- **Context:** high or changing tab-list RTT, tick gaps, large position discontinuities, vehicles, horizontal collisions, water/lava, ladders, movement potions, and hurt/knockback windows suppress affected observations. These are quality gates, not evidence of innocence. Tab-list RTT is only a proxy for network conditions.
- **Confidence:** compact chat reviews show `N% evidence confidence`. The bounded 0–85 index uses anomaly strength, observation reliability, independent episodes, detector-group diversity, and clean-observation decay. Correlated aim/block and edge-rhythm signals share groups, so the same event cannot multiply votes. This percentage describes confidence in the *observed suspicious pattern*, **not a calibrated probability of cheating**; no labeled observer corpus exists to justify that claim. Review alerts remain non-punitive.
- **UI:** added the toggle, scaled the panel and pointer coordinates for small viewports, stopped simultaneous tab content rendering, and moved the existing Notifications controls into separate grid cells. The flash card, evidence button, and Debug card no longer overlap.

## Validation and performance

- Full Forge, Badlion, and native release build passed. Native compatibility and layout CTests, bootstrap tests, detector static checks, release verification, and `git diff --check` passed.
- Detector harnesses cover gross/subtle reach, crowded attribution, network and tick gaps, evidence dependence and decay, temporal toggle, repeated edge patterns, variable skilled bridging, high ping, uncertain terrain, and non-building edge crouches. GUI layout was checked at five scaled viewport sizes; native layout tests also cover DPI variants.
- History is fixed-size per tracked UUID: 80 sample references in each of two bounded rings, with twelve edge events. No world chunks or replay data are copied. The calm-turn median is calculated only when an aim review is being considered. Edge block queries run only for grounded block holders with usable movement.

## Deliberate limits

SafeWalk edge stops, placement attribution/scaffold geometry, click/CPS automation, partial velocity, ordinary sprint resets, jump macros, and subtle aim/reach are not promoted to named alerts. Rain lacks the suspect's input packets, confirmed placer, known velocity impulse, and calibrated server policy needed to distinguish those from skilled or lagged play reliably. Human Eagle bridging can still resemble automated crouching, so this candidate stays review-only. Synthetic tests and build checks do not establish a real-world false-positive rate; controlled observer/server captures and skilled-player validation are still required for probability calibration.
