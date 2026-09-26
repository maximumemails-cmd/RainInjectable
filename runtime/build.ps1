# Compiles runtime\src with JDK 8 javac against the SRG-named jars in runtime\libs
# and produces runtime\build\rain-runtime.jar.
#
# Env:
#   RAIN_JDK8   optional path to a JDK 8 root (bin\javac.exe, bin\jar.exe). If not
#               set, common Eclipse Adoptium install roots are probed.
$ErrorActionPreference = 'Stop'

$root  = $PSScriptRoot
$src   = Join-Path $root 'src'
$res   = Join-Path $root 'resources'
$libs  = Join-Path $root 'libs'
$build = Join-Path $root 'build'
$cls   = Join-Path $build 'classes'
$out   = Join-Path $build 'rain-runtime.jar'

function Find-Jdk8 {
    if ($env:RAIN_JDK8 -and (Test-Path (Join-Path $env:RAIN_JDK8 'bin\javac.exe'))) { return $env:RAIN_JDK8 }
    $roots = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu")
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        $hit = Get-ChildItem -Path $r -Directory | Where-Object { $_.Name -match '^(jdk-?8|jdk1\.8|zulu8|jdk-8)' } |
               Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

$jdk = Find-Jdk8
if (-not $jdk) { throw 'JDK 8 not found. Set RAIN_JDK8 to a JDK 8 install root.' }
$javac = Join-Path $jdk 'bin\javac.exe'
$jar   = Join-Path $jdk 'bin\jar.exe'
Write-Host "using JDK 8 at $jdk"

$mcSrg    = Join-Path $libs 'minecraft-1.8.9-srg.jar'
$forgeSrg = Join-Path $libs 'forge-1.8.9-universal-srg.jar'
$lwjgl    = Join-Path $libs 'lwjgl-2.9.4.jar'
foreach ($f in @($mcSrg, $forgeSrg, $lwjgl)) {
    if (-not (Test-Path $f)) { throw "missing $f - run runtime\setup.ps1 first" }
}

if (Test-Path $cls) { Remove-Item -Recurse -Force $cls }
New-Item -ItemType Directory -Force -Path $cls | Out-Null

$sources = Get-ChildItem -Path $src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
$listFile = Join-Path $build 'sources.txt'
$sources | Set-Content -Path $listFile -Encoding ASCII
Write-Host "compiling $($sources.Count) sources..."

$cp = "$mcSrg;$forgeSrg;$lwjgl"
& $javac -source 1.8 -target 1.8 -encoding UTF-8 -Xlint:-options -g -d $cls -cp $cp "@$listFile"
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Copy-Item -Path (Join-Path $res '*') -Destination $cls -Recurse -Force

$manifest = Join-Path $build 'MANIFEST.MF'
@(
    'Manifest-Version: 1.0',
    'Implementation-Title: Rain Anticheat (injectable runtime)',
    'Implementation-Vendor: Rain-Anticheat / RainInjectable',
    'Rain-Bootstrap-Class: first.rain.anticheat.bootstrap.RainBootstrap',
    ''
) | Set-Content -Path $manifest -Encoding ASCII

if (Test-Path $out) { Remove-Item -Force $out }
& $jar cfm $out $manifest -C $cls .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

Write-Host "built: $out"
