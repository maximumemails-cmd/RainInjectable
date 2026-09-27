param([string]$DistName = 'dist')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root $DistName
$release = Join-Path $root 'releases\1.3.0-injectable'
$exe = Join-Path $release 'RainInjectable.exe'
$dll = Join-Path $dist 'rain-payload.dll'
$jar = Join-Path $dist 'rain-runtime.jar'
$badlionJar = Join-Path $dist 'rain-badlion.jar'
$zip = Join-Path $release 'RainInjectable-Windows-x64.zip'
$releaseDll = Join-Path $release 'rain-payload.dll'
$releaseJar = Join-Path $release 'rain-runtime.jar'
$releaseBadlionJar = Join-Path $release 'rain-badlion.jar'
foreach ($file in @($exe, $dll, $jar, $badlionJar, $zip, $releaseDll, $releaseJar, $releaseBadlionJar)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing artifact: $file" }
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $dll).Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $releaseDll).Hash -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $releaseJar).Hash -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $badlionJar).Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $releaseBadlionJar).Hash) {
    throw 'Release DLL/JAR copies differ from the verified build outputs'
}

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class RainResourceReader {
    [DllImport("kernel32.dll", EntryPoint="LoadLibraryExW", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern IntPtr LoadLibraryEx(string file, IntPtr reserved, uint flags);
    [DllImport("kernel32.dll", EntryPoint="FindResourceW", SetLastError=true)]
    static extern IntPtr FindResource(IntPtr module, IntPtr name, IntPtr type);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern IntPtr LoadResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern IntPtr LockResource(IntPtr resource);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern uint SizeofResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool FreeLibrary(IntPtr module);
    public static byte[] Read(string file, int id) {
        IntPtr module = LoadLibraryEx(file, IntPtr.Zero, 2);
        if (module == IntPtr.Zero) throw new Exception("LoadLibraryEx failed");
        try {
            IntPtr resource = FindResource(module, (IntPtr)id, (IntPtr)10);
            if (resource == IntPtr.Zero) throw new Exception("Embedded resource missing: " + id);
            uint size = SizeofResource(module, resource);
            IntPtr pointer = LockResource(LoadResource(module, resource));
            if (pointer == IntPtr.Zero || size == 0) throw new Exception("Embedded resource invalid: " + id);
            byte[] data = new byte[size];
            Marshal.Copy(pointer, data, 0, (int)size);
            return data;
        } finally { FreeLibrary(module); }
    }
}
'@

function Hash-Bytes([byte[]]$bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Machine([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $offset = [BitConverter]::ToInt32($bytes, 0x3c)
    return [BitConverter]::ToUInt16($bytes, $offset + 4)
}
if ((Machine $exe) -ne 0x8664 -or (Machine $dll) -ne 0x8664) { throw 'Native architecture is not x64' }
if ((Hash-Bytes ([RainResourceReader]::Read($exe, 101))) -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $dll).Hash.ToLowerInvariant()) {
    throw 'Embedded DLL differs from the built payload'
}
if ((Hash-Bytes ([RainResourceReader]::Read($exe, 102))) -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash.ToLowerInvariant()) {
    throw 'Embedded JAR differs from the built runtime'
}
if ((Hash-Bytes ([RainResourceReader]::Read($exe, 103))) -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $badlionJar).Hash.ToLowerInvariant()) {
    throw 'Embedded Badlion JAR differs from the built runtime'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$versionHeader = Get-Content -LiteralPath (Join-Path $root 'injector\src\version.h') -Raw
if ($versionHeader -notmatch 'RAIN_INJECTABLE_VERSION L"([^"]+)"') { throw 'Injector version marker missing' }
$version = $Matches[1]
$runtimeArchive = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($className in @('first/rain/anticheat/RainCore.class', 'first/rain/anticheat/bootstrap/RainBootstrap.class')) {
        $entry = $runtimeArchive.GetEntry($className)
        if (-not $entry) { throw "Runtime class missing: $className" }
        $stream = $entry.Open()
        $bytes = New-Object System.IO.MemoryStream
        try { $stream.CopyTo($bytes) } finally { $stream.Dispose() }
        if (-not [System.Text.Encoding]::ASCII.GetString($bytes.ToArray()).Contains($version)) {
            throw "Version mismatch: $className"
        }
        $bytes.Dispose()
    }
} finally { $runtimeArchive.Dispose() }
$badlionArchive = [System.IO.Compression.ZipFile]::OpenRead($badlionJar)
try {
    if (-not $badlionArchive.GetEntry('first/rain/anticheat/badlion/BadlionBootstrap.class')) {
        throw 'Badlion runtime entry point is missing'
    }
    foreach ($entry in $badlionArchive.Entries) {
        if ($entry.FullName -match '^(lion/|com/lionclient/|net/minecraft/|net/minecraftforge/)') {
            throw "Unexpected bundled class in Badlion runtime: $($entry.FullName)"
        }
    }
} finally { $badlionArchive.Dispose() }
$archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
try {
    $names = @($archive.Entries | ForEach-Object { $_.FullName })
    foreach ($required in @('RainInjectable.exe', 'LICENSE', 'LICENSE-NOTES.md', 'CREDITS.md', 'README.md', 'RELEASE_NOTES.md', 'NEXT_GEN_IMPLEMENTATION_SUMMARY.md', 'RAIN_DETECTION_UPGRADE_REPORT.md')) {
        if ($names -notcontains $required) { throw "Release ZIP lacks $required" }
    }
    if ($names.Count -ne 8) { throw 'Release ZIP contains unexpected files' }
} finally { $archive.Dispose() }

Write-Host "Release verification OK: $version, x64, embedded DLL and JARs byte-for-byte, ZIP contents"
$hashLines = @(Get-FileHash -Algorithm SHA256 -LiteralPath $exe, $releaseDll, $releaseJar, $releaseBadlionJar, $zip |
    ForEach-Object { "$($_.Hash.ToLowerInvariant())  $(Split-Path -Leaf $_.Path)" })
$hashLines | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') -Encoding ASCII
$hashLines | ForEach-Object { Write-Host $_ }
