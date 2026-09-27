# Build a separate Rain detector JAR for Badlion's obfuscated Minecraft 1.8.9.
# No Lion client classes or Forge shim classes are included.
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = $PSScriptRoot
$libs = Join-Path $root 'libs'
$build = Join-Path $root 'build\badlion'
$classes = Join-Path $build 'classes'
$map = Join-Path $build 'joined.srg'
$expandedMap = Join-Path $build 'joined-expanded.srg'
$toolClasses = Join-Path $build 'tools'
$out = Join-Path $root 'build\rain-badlion.jar'
$srgJar = Join-Path $build 'rain-badlion-srg.jar'
$mcpZip = Join-Path $libs 'mcp-1.8.9-srg.zip'
$remapper = Join-Path $libs 'SpecialSource-shaded.jar'

function Find-Jdk8 {
    if ($env:RAIN_JDK8 -and (Test-Path (Join-Path $env:RAIN_JDK8 'bin\javac.exe'))) { return $env:RAIN_JDK8 }
    $adoptium = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
    Get-ChildItem $adoptium -Directory | Where-Object { $_.Name -match '^jdk-?8' } |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
        Select-Object -First 1 -ExpandProperty FullName
}
$jdk = Find-Jdk8
if (-not $jdk) { throw 'JDK 8 not found. Set RAIN_JDK8.' }

if (-not (Test-Path $mcpZip)) {
    Invoke-WebRequest -Uri 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8.9/mcp-1.8.9-srg.zip' -OutFile $mcpZip -UseBasicParsing
}
if (-not (Test-Path $remapper)) {
    Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.11.4/SpecialSource-1.11.4-shaded.jar' -OutFile $remapper -UseBasicParsing
}

New-Item -ItemType Directory -Force -Path $build | Out-Null
$resolvedBuild = [IO.Path]::GetFullPath($build)
$resolvedClasses = [IO.Path]::GetFullPath($classes)
if (-not $resolvedClasses.StartsWith($resolvedBuild + [IO.Path]::DirectorySeparatorChar,
                                    [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to clear classes outside the Badlion build directory.'
}
if (Test-Path $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$zip = [IO.Compression.ZipFile]::OpenRead($mcpZip)
try {
    $entry = $zip.GetEntry('joined.srg')
    if (-not $entry) { throw 'joined.srg not found in MCP mappings' }
    $inputStream = $entry.Open()
    $outputStream = [IO.File]::Create($map)
    try { $inputStream.CopyTo($outputStream) }
    finally { $inputStream.Dispose(); $outputStream.Dispose() }
} finally { $zip.Dispose() }

$base = Join-Path $root 'src\first\rain\anticheat'
$alt = Join-Path $root 'badlion-src\first\rain\anticheat'
$sources = @(
    (Join-Path $alt 'Rain.java'),
    (Join-Path $alt 'RainCore.java'),
    (Join-Path $alt 'badlion\BadlionBootstrap.java'),
    (Join-Path $alt 'gui\ClickGuiKeybind.java'),
    (Join-Path $alt 'util\anticheat\FlashNotification.java'),
    (Join-Path $base 'config\cfg.java'),
    (Join-Path $base 'gui\ClickGui.java'),
    (Join-Path $base 'gui\ModuleCard.java'),
    (Join-Path $base 'gui\FlashSettingsCard.java'),
    (Join-Path $base 'gui\NametagSettingsCard.java'),
    (Join-Path $base 'util\RenderUtil.java'),
    (Join-Path $base 'util\anticheat\PlayerEligibility.java'),
    (Join-Path $base 'util\anticheat\AntiCheatData.java'),
    (Join-Path $base 'util\anticheat\AlertManager.java')
)
$sources += Get-ChildItem (Join-Path $base 'util\anticheat\checks') -Filter '*.java' | ForEach-Object FullName
$list = Join-Path $build 'sources.txt'
$sources | Set-Content -LiteralPath $list -Encoding ASCII
$cp = "$(Join-Path $libs 'minecraft-1.8.9-srg.jar');$(Join-Path $libs 'lwjgl-2.9.4.jar')"
& (Join-Path $jdk 'bin\javac.exe') -source 1.8 -target 1.8 -encoding UTF-8 -Xlint:-options -g -cp $cp -d $classes "@$list"
if ($LASTEXITCODE -ne 0) { throw 'Badlion detector compilation failed' }

& (Join-Path $jdk 'bin\jar.exe') cf $srgJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Could not package Badlion detector classes' }
New-Item -ItemType Directory -Force -Path $toolClasses | Out-Null
& (Join-Path $jdk 'bin\javac.exe') -source 1.8 -target 1.8 -Xlint:-options -cp $remapper -d $toolClasses (Join-Path $root 'tools\ExpandMappings.java')
if ($LASTEXITCODE -ne 0) { throw 'Mapping expander compilation failed' }
& (Join-Path $jdk 'bin\java.exe') -cp "$toolClasses;$remapper" ExpandMappings $map $srgJar $expandedMap
if ($LASTEXITCODE -ne 0) { throw 'Inherited mapping expansion failed' }
& (Join-Path $jdk 'bin\java.exe') -jar $remapper --in-jar $srgJar --out-jar $out --srg-in $expandedMap --reverse
if ($LASTEXITCODE -ne 0) { throw 'Badlion detector reobfuscation failed' }
$archive = [IO.Compression.ZipFile]::OpenRead($out)
try {
    foreach ($entry in $archive.Entries) {
        if (-not $entry.FullName.EndsWith('.class')) { continue }
        if ($entry.FullName.StartsWith('net/minecraft/') -or
            $entry.FullName.StartsWith('net/minecraftforge/') -or
            $entry.FullName.StartsWith('lion/') -or
            $entry.FullName.StartsWith('com/lionclient/')) {
            throw "Unexpected class in Badlion detector JAR: $($entry.FullName)"
        }
        $stream = $entry.Open()
        $bytes = New-Object IO.MemoryStream
        try { $stream.CopyTo($bytes) } finally { $stream.Dispose() }
        $classText = [Text.Encoding]::ASCII.GetString($bytes.ToArray())
        $bytes.Dispose()
        if ($classText -match '(?:func|field)_\d+_[A-Za-z]+') {
            throw "Unmapped Minecraft member in $($entry.FullName): $($Matches[0])"
        }
        if ($classText.Contains('net/minecraftforge/') -or $classText.Contains('com/lionclient/')) {
            throw "Forge or Lion reference in Badlion detector class: $($entry.FullName)"
        }
    }
} finally { $archive.Dispose() }
Write-Host "built: $out"
