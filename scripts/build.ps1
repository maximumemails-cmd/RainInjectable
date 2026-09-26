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
    [string]$Config = 'Release'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root 'dist'
$nativeBuild = Join-Path $root 'build\native'

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
}

# ---- native --------------------------------------------------------------
if (-not $SkipNative) {
    Write-Host '== configuring native (CMake, Visual Studio 17 2022, x64) =='
    & cmake -S (Join-Path $root 'injector') -B $nativeBuild -G 'Visual Studio 17 2022' -A x64 "-DJNI_ROOT=$jdk8"
    if ($LASTEXITCODE -ne 0) { throw 'cmake configure failed' }
    Write-Host "== building native ($Config) =="
    & cmake --build $nativeBuild --config $Config
    if ($LASTEXITCODE -ne 0) { throw 'cmake build failed' }
}

# ---- stage ---------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$exe = Join-Path $nativeBuild "$Config\RainInjector.exe"
$dll = Join-Path $nativeBuild "$Config\rain-payload.dll"
$jar = Join-Path $root 'runtime\build\rain-runtime.jar'
foreach ($f in @($exe, $dll, $jar)) {
    if (-not (Test-Path $f)) { throw "expected build output missing: $f" }
    Copy-Item $f $dist -Force
}
Copy-Item (Join-Path $root 'LICENSE') $dist -Force
Copy-Item (Join-Path $root 'docs\LICENSE-NOTES.md') $dist -Force

Write-Host ''
Write-Host "staged to $dist"
Get-ChildItem $dist | Format-Table -AutoSize Name, Length, LastWriteTime | Out-String | Write-Host
