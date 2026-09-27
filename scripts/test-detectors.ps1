$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jdk = $env:RAIN_JDK8
if (-not $jdk) {
    $jdk = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory |
        Where-Object { $_.Name -match '^jdk-?8' } | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) { throw 'JDK 8 not found (set RAIN_JDK8)' }
$javac = Join-Path $jdk 'bin\javac.exe'
$java = Join-Path $jdk 'bin\java.exe'
$out = Join-Path $root 'build\detector-harness'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$checks = Join-Path $root 'runtime\src\first\rain\anticheat\util\anticheat\checks'
& $javac -source 1.8 -target 1.8 -Xlint:-options -d $out `
    (Join-Path $checks 'EpisodeEvidence.java') (Join-Path $checks 'AimEvidence.java') `
    (Join-Path $checks 'AimGeometry.java') `
    (Join-Path $checks 'AutoBlockEvidence.java') (Join-Path $checks 'ScaffoldEvidence.java') `
    (Join-Path $checks 'ObservationEngine.java') (Join-Path $checks 'EvidenceLedger.java') `
    (Join-Path $checks 'EvidenceExporter.java') `
    (Join-Path $root 'tests\detector-harness\Harness.java') `
    (Join-Path $root 'tests\detector-harness\ObservationHarness.java') `
    (Join-Path $root 'tests\detector-harness\EvidenceHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'detector harness compilation failed' }
& $java -cp $out Harness
if ($LASTEXITCODE -ne 0) { throw 'detector harness failed' }
& $java -cp $out ObservationHarness
if ($LASTEXITCODE -ne 0) { throw 'observation harness failed' }
& $java -cp $out EvidenceHarness
if ($LASTEXITCODE -ne 0) { throw 'evidence harness failed' }
