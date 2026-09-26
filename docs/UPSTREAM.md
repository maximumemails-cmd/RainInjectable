# Upstream sources (pinned)

| Project | URL | Ref | Commit SHA |
|---|---|---|---|
| LionInjectable | https://github.com/LionClientINC/LionInjectable | tag `V1.0.5` | `6a04238f67902472ea2f69e6d683cdfb89aded9f` |
| Rain-Anticheat | https://github.com/JasonWangFTW/Rain-Anticheat | `main` (HEAD at clone time, 2026-09-26) | `40974c3fd35bfca63e89b07b87c430d5dd730d83` |

Note: Lion's `main` HEAD (`2223b6a`) is one README-only commit ahead of `V1.0.5`.
Rain has a single tag `alerts`; `main` HEAD was used as instructed.

Re-fetch both at these exact SHAs with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\fetch-upstream.ps1
```
