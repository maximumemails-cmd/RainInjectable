# Downloads and prepares the compile-time dependencies for rain-runtime.jar
# into runtime\libs (git-ignored). Adapted from upstream Rain-Anticheat setup.ps1.
#   - vanilla 1.8.9 client, remapped to SRG names with SpecialSource
#   - Forge 1.8.9-11.15.1.2318 universal, remapped to SRG names
#   - LWJGL 2.9.4
# Idempotent: skips anything already present.
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = $PSScriptRoot
$libs = Join-Path $root 'libs'
New-Item -ItemType Directory -Force -Path $libs | Out-Null

$java = $env:RAIN_JAVA
if (-not $java) { $java = 'java' }

function Get-File($url, $dest) {
    if (Test-Path $dest) { return }
    Write-Host "downloading: $url"
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
}

$mcNotch   = Join-Path $libs 'minecraft-1.8.9-notch.jar'
$mcSrg     = Join-Path $libs 'minecraft-1.8.9-srg.jar'
$forgeJar  = Join-Path $libs 'forge-1.8.9-universal.jar'
$forgeSrg  = Join-Path $libs 'forge-1.8.9-universal-srg.jar'
$mcpZip    = Join-Path $libs 'mcp-1.8.9-srg.zip'
$mcpDir    = Join-Path $libs 'mcp-srg'
$ssJar     = Join-Path $libs 'SpecialSource-shaded.jar'
$lwjglJar  = Join-Path $libs 'lwjgl-2.9.4.jar'

if (-not (Test-Path $mcNotch)) {
    Write-Host 'resolving 1.8.9 client URL from Mojang version manifest...'
    $manifest = Invoke-RestMethod 'https://launchermeta.mojang.com/mc/game/version_manifest.json'
    $ver = $manifest.versions | Where-Object { $_.id -eq '1.8.9' } | Select-Object -First 1
    if (-not $ver) { throw 'version 1.8.9 not found in manifest' }
    $verJson = Invoke-RestMethod $ver.url
    Get-File $verJson.downloads.client.url $mcNotch
}

Get-File 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar' $forgeJar
Get-File 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8.9/mcp-1.8.9-srg.zip' $mcpZip
Get-File 'https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.11.4/SpecialSource-1.11.4-shaded.jar' $ssJar
Get-File 'https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar' $lwjglJar

if (-not (Test-Path $mcpDir)) {
    Expand-Archive -Path $mcpZip -DestinationPath $mcpDir -Force
}
$srgFile = Get-ChildItem -Path $mcpDir -Recurse -Filter 'notch-srg.srg' | Select-Object -First 1
if (-not $srgFile) { throw 'notch-srg.srg not found inside mcp-1.8.9-srg.zip' }

if (-not (Test-Path $mcSrg)) {
    Write-Host 'remapping vanilla jar to Searge names (takes ~30s)...'
    & $java -jar $ssJar --in-jar $mcNotch --out-jar $mcSrg --srg-in $srgFile.FullName
    if ($LASTEXITCODE -ne 0) { throw 'SpecialSource failed on vanilla jar' }
}

if (-not (Test-Path $forgeSrg)) {
    Write-Host 'remapping forge universal references to Searge names...'
    & $java -jar $ssJar --in-jar $forgeJar --out-jar $forgeSrg --srg-in $srgFile.FullName --live
    if ($LASTEXITCODE -ne 0) { throw 'SpecialSource failed on forge jar' }
}

Write-Host "runtime deps ready in $libs"
