# Verifies that everything needed to build and (later) live-test RainInjectable
# is present. Prints a table and exits 1 if any REQUIRED item is missing.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\check-environment.ps1
$ErrorActionPreference = 'SilentlyContinue'

$results = @()
function Add-Check($name, $ok, $detail, $required = $true) {
    $script:results += [pscustomobject]@{ Check = $name; Status = $(if ($ok) { 'OK' } elseif ($required) { 'MISSING' } else { 'optional' }); Detail = $detail; Required = $required }
}

# OS / arch
$os = Get-CimInstance Win32_OperatingSystem
Add-Check 'Windows x64' ($env:PROCESSOR_ARCHITECTURE -eq 'AMD64') "$($os.Caption) $($os.Version)"

# git
$git = Get-Command git; Add-Check 'git' ($null -ne $git) $(if ($git) { (& git --version) } else { 'not on PATH' })

# cmake
$cmake = Get-Command cmake; Add-Check 'cmake' ($null -ne $cmake) $(if ($cmake) { (& cmake --version | Select-Object -First 1) } else { 'not on PATH' })

# MSVC via vswhere
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$vsPath = $null
if (Test-Path $vswhere) {
    $vsPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null | Select-Object -First 1
}
Add-Check 'MSVC x64 toolset (VS 2022 / Build Tools)' ($null -ne $vsPath -and $vsPath -ne '') $(if ($vsPath) { $vsPath } else { 'install "Desktop development with C++" via VS Build Tools' })

# Windows SDK
$sdk = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\Include" -Directory | Sort-Object Name -Descending | Select-Object -First 1
Add-Check 'Windows 10/11 SDK' ($null -ne $sdk) $(if ($sdk) { $sdk.Name } else { 'not found' })

# JDK 8 (javac + jni.h)
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
$jniOk = $jdk8 -and (Test-Path (Join-Path $jdk8 'include\jni.h')) -and (Test-Path (Join-Path $jdk8 'include\win32\jni_md.h'))
Add-Check 'JDK 8 (javac, jar)' ($null -ne $jdk8) $(if ($jdk8) { $jdk8 } else { 'install Temurin 8 or set RAIN_JDK8' })
Add-Check 'JDK 8 JNI headers (jni.h, jni_md.h)' $jniOk $(if ($jniOk) { Join-Path $jdk8 'include' } else { 'missing' })

# any java on PATH (used by runtime\setup.ps1 to run SpecialSource)
$java = Get-Command java; Add-Check 'java on PATH (for setup.ps1 remap step)' ($null -ne $java) $(if ($java) { (& java -version 2>&1 | Select-Object -First 1) } else { 'not on PATH' })

# PowerShell
Add-Check 'PowerShell >= 5.1' ($PSVersionTable.PSVersion.Major -ge 5) $PSVersionTable.PSVersion.ToString()

# Optional: ninja
$ninja = Get-Command ninja; Add-Check 'ninja (optional, not used)' ($null -ne $ninja) $(if ($ninja) { (& ninja --version) } else { '-' }) $false

# Live-test prerequisites (optional for building)
$mcDir = Join-Path $env:APPDATA '.minecraft'
$forgeVer = Get-ChildItem (Join-Path $mcDir 'versions') -Directory | Where-Object { $_.Name -match '1\.8\.9.*forge|forge.*1\.8\.9' } | Select-Object -First 1
$forgeLib = Test-Path (Join-Path $mcDir 'libraries\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9')
Add-Check 'Forge 1.8.9 profile in .minecraft (live test only)' ($null -ne $forgeVer -or $forgeLib) $(if ($forgeVer) { $forgeVer.Name } elseif ($forgeLib) { 'forge library present' } else { 'install Forge 1.8.9 (11.15.1.2318) or use Feather' }) $false
$feather = Test-Path (Join-Path $env:APPDATA '.feather')
Add-Check 'Feather launcher (live test alternative)' $feather $(if ($feather) { Join-Path $env:APPDATA '.feather' } else { '-' }) $false

$results | Format-Table -AutoSize Check, Status, Detail | Out-String -Width 200 | Write-Host

$missing = $results | Where-Object { $_.Required -and $_.Status -ne 'OK' }
if ($missing) {
    Write-Host "MISSING required items: $($missing.Check -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host 'environment OK' -ForegroundColor Green
exit 0
