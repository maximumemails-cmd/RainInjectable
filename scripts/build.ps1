# One-shot build: runtime jar (JDK 8 javac) + native injector/payload (CMake + MSVC x64),
# then stages a release into dist\.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\build.ps1 [-SkipNative] [-SkipRuntime] [-Config Release|Debug]
#
# Env (optional): RAIN_JDK8 = JDK 8 root (bin\javac.exe, include\jni.h). Auto-probed otherwise.
param(
    [switch]$SkipNative,
    [switch]$SkipRuntime,
    [string]$Config = 'Release',
    [string]$NativeBuildName = 'native',
    [string]$DistName = 'dist'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root $DistName
$nativeBuild = Join-Path (Join-Path $root 'build') $NativeBuildName
$releaseDir = Join-Path $root 'releases\1.1.0-injectable'

function Find-Jdk8 {
    if ($env:RAIN_JDK8 -and (Test-Path (Join-Path $env:RAIN_JDK8 'bin\javac.exe'))) { return $env:RAIN_JDK8 }
    foreach ($r in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu")) {
        if (-not (Test-Path $r)) { continue }
        $hit = Get-ChildItem $r -Directory | Where-Object { $_.Name -match '^(jdk-?8|jdk1\.8|zulu8)' } |
               Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}
$jdk8 = Find-Jdk8
if (-not $jdk8) { throw 'JDK 8 not found. Install Temurin 8 or set RAIN_JDK8.' }
$env:RAIN_JDK8 = $jdk8
Write-Host "JDK 8: $jdk8"

# ---- runtime -------------------------------------------------------------
if (-not $SkipRuntime) {
    $libs = Join-Path $root 'runtime\libs'
    if (-not (Test-Path (Join-Path $libs 'minecraft-1.8.9-srg.jar')) -or
        -not (Test-Path (Join-Path $libs 'forge-1.8.9-universal-srg.jar')) -or
        -not (Test-Path (Join-Path $libs 'lwjgl-2.9.4.jar'))) {
        Write-Host '== runtime deps missing, running runtime\setup.ps1 =='
        $env:RAIN_JAVA = Join-Path $jdk8 'bin\java.exe'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'runtime\setup.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'runtime\setup.ps1 failed' }
    }
    Write-Host '== building rain-runtime.jar =='
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'runtime\build.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'runtime build failed' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'runtime\build-badlion.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Badlion runtime build failed' }
}

# ---- native --------------------------------------------------------------
if (-not $SkipNative) {
    Write-Host '== configuring native (CMake, Visual Studio 17 2022, x64) =='
    $runtimeJar = Join-Path $root 'runtime\build\rain-runtime.jar'
    $badlionJar = Join-Path $root 'runtime\build\rain-badlion.jar'
    & cmake -S (Join-Path $root 'injector') -B $nativeBuild -G 'Visual Studio 17 2022' -A x64 "-DJNI_ROOT=$jdk8" "-DRUNTIME_JAR=$runtimeJar" "-DBADLION_JAR=$badlionJar"
    if ($LASTEXITCODE -ne 0) { throw 'cmake configure failed' }
    Write-Host "== building native ($Config) =="
    & cmake --build $nativeBuild --config $Config
    if ($LASTEXITCODE -ne 0) { throw 'cmake build failed' }
}

# ---- stage ---------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Remove-Item -LiteralPath (Join-Path $dist 'rain-bootstrap.log') -Force -ErrorAction SilentlyContinue
$exe = Join-Path $nativeBuild "$Config\RainInjectable.exe"
$dll = Join-Path $nativeBuild "$Config\rain-payload.dll"
$jar = Join-Path $root 'runtime\build\rain-runtime.jar'
$badlionJar = Join-Path $root 'runtime\build\rain-badlion.jar'
foreach ($f in @($exe, $dll, $jar, $badlionJar)) {
    if (-not (Test-Path $f)) { throw "expected build output missing: $f" }
    Copy-Item $f $dist -Force
}
Copy-Item (Join-Path $root 'LICENSE') $dist -Force
Copy-Item (Join-Path $root 'docs\LICENSE-NOTES.md') $dist -Force

New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
Copy-Item $exe (Join-Path $releaseDir 'RainInjectable.exe') -Force
Copy-Item $dll (Join-Path $releaseDir 'rain-payload.dll') -Force
Copy-Item $jar (Join-Path $releaseDir 'rain-runtime.jar') -Force
Copy-Item $badlionJar (Join-Path $releaseDir 'rain-badlion.jar') -Force
Copy-Item (Join-Path $root 'docs\RELEASE_NOTES.md') (Join-Path $releaseDir 'RELEASE_NOTES.md') -Force
$bundleStage = Join-Path $root 'build\release-bundle'
New-Item -ItemType Directory -Force -Path $bundleStage | Out-Null
Copy-Item $exe (Join-Path $bundleStage 'RainInjectable.exe') -Force
Copy-Item (Join-Path $root 'LICENSE') $bundleStage -Force
Copy-Item (Join-Path $root 'docs\LICENSE-NOTES.md') $bundleStage -Force
Copy-Item (Join-Path $root 'CREDITS.md') $bundleStage -Force
Copy-Item (Join-Path $root 'README.md') $bundleStage -Force
Copy-Item (Join-Path $root 'docs\RELEASE_NOTES.md') $bundleStage -Force
$bundle = Join-Path $releaseDir 'RainInjectable-Windows-x64.zip'
Compress-Archive -LiteralPath (Join-Path $bundleStage 'RainInjectable.exe'),
    (Join-Path $bundleStage 'LICENSE'),
    (Join-Path $bundleStage 'LICENSE-NOTES.md'),
    (Join-Path $bundleStage 'CREDITS.md'),
    (Join-Path $bundleStage 'README.md'),
    (Join-Path $bundleStage 'RELEASE_NOTES.md') -DestinationPath $bundle -Force

Write-Host ''
Write-Host "staged to $dist"
Get-ChildItem $dist | Format-Table -AutoSize Name, Length, LastWriteTime | Out-String | Write-Host
Write-Host "single-file release: $(Join-Path $releaseDir 'RainInjectable.exe')"
Write-Host "release bundle: $bundle"
