# Static smoke test of the injection bootstrap without Minecraft.
# Compiles tests\bootstrap-harness with JDK 8 and runs it in both classloader
# modes against dist\rain-runtime.jar (or runtime\build\rain-runtime.jar).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$jdk = $env:RAIN_JDK8
if (-not $jdk) {
    $jdk = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory | Where-Object { $_.Name -match '^jdk-?8' } | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) { throw 'JDK 8 not found (set RAIN_JDK8)' }
$javac = Join-Path $jdk 'bin\javac.exe'
$java  = Join-Path $jdk 'bin\java.exe'

$jar = Join-Path $root 'dist\rain-runtime.jar'
if (-not (Test-Path $jar)) { $jar = Join-Path $root 'runtime\build\rain-runtime.jar' }
if (-not (Test-Path $jar)) { throw 'rain-runtime.jar not built - run scripts\build.ps1 first' }

$libs = Join-Path $root 'runtime\libs'
$libJars = @('minecraft-1.8.9-srg.jar', 'forge-1.8.9-universal-srg.jar', 'lwjgl-2.9.4.jar') | ForEach-Object { Join-Path $libs $_ }
foreach ($l in $libJars) { if (-not (Test-Path $l)) { throw "missing $l - run runtime\setup.ps1" } }

$h = Join-Path $root 'tests\bootstrap-harness'
$outFakes = Join-Path $root 'build\harness\fakes'
$outHarn  = Join-Path $root 'build\harness\harness'
New-Item -ItemType Directory -Force -Path $outFakes, $outHarn | Out-Null

& $javac -source 1.8 -target 1.8 -Xlint:-options -d $outFakes (Get-ChildItem (Join-Path $h 'fakes') -Recurse -Filter *.java | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw 'javac fakes failed' }
& $javac -source 1.8 -target 1.8 -Xlint:-options -d $outHarn (Join-Path $h 'harness\Harness.java')
if ($LASTEXITCODE -ne 0) { throw 'javac harness failed' }

$logDir = Split-Path -Parent $jar
$log = Join-Path $logDir 'rain-bootstrap.log'
if (Test-Path $log) { Remove-Item $log -Force }

Write-Host '== mode: system (Launch on system classpath) =='
& $java -cp "$outHarn;$outFakes" Harness system $jar $outFakes @libJars
if ($LASTEXITCODE -ne 0) { throw 'harness FAILED (system mode)' }

Write-Host '== mode: wrapper (Launch only in a thread context classloader) =='
& $java -cp "$outHarn" Harness wrapper $jar $outFakes @libJars
if ($LASTEXITCODE -ne 0) { throw 'harness FAILED (wrapper mode)' }

Write-Host ''
Write-Host "rain-bootstrap.log written by the harness runs:"
Get-Content $log | ForEach-Object { "  $_" }
Remove-Item $log -Force
Write-Host 'bootstrap harness OK'
