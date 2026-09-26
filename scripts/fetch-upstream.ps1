# Re-clones the two upstream projects into upstream\ at the pinned SHAs
# recorded in docs\UPSTREAM.md. Idempotent.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$up = Join-Path $root 'upstream'
New-Item -ItemType Directory -Force -Path $up | Out-Null

$pins = @(
    @{ Name = 'LionInjectable'; Url = 'https://github.com/LionClientINC/LionInjectable'; Sha = '6a04238f67902472ea2f69e6d683cdfb89aded9f' },
    @{ Name = 'Rain-Anticheat'; Url = 'https://github.com/JasonWangFTW/Rain-Anticheat';  Sha = '40974c3fd35bfca63e89b07b87c430d5dd730d83' }
)

foreach ($p in $pins) {
    $dir = Join-Path $up $p.Name
    if (-not (Test-Path (Join-Path $dir '.git'))) {
        Write-Host "cloning $($p.Name)..."
        git clone --quiet $p.Url $dir
        if ($LASTEXITCODE -ne 0) { throw "git clone failed for $($p.Name)" }
    }
    Push-Location $dir
    try {
        git fetch --quiet origin
        git checkout --quiet --detach $p.Sha
        if ($LASTEXITCODE -ne 0) { throw "checkout of $($p.Sha) failed in $($p.Name)" }
        Write-Host ("{0,-16} {1}" -f $p.Name, (git rev-parse HEAD))
    } finally { Pop-Location }
}
