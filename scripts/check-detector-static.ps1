$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src = Join-Path $root 'runtime\src\first\rain\anticheat'
function Assert-Contains($file, $pattern, $description) {
    if (-not (Select-String -Path $file -Pattern $pattern -Quiet)) { throw "missing $description" }
}
function Assert-Omits($file, $pattern, $description) {
    if (Select-String -Path $file -Pattern $pattern -Quiet) { throw "unexpected $description" }
}
$rain = Join-Path $src 'Rain.java'
$core = Join-Path $src 'RainCore.java'
$data = Join-Path $src 'util\anticheat\AntiCheatData.java'
$alerts = Join-Path $src 'util\anticheat\AlertManager.java'
$config = Join-Path $src 'config\cfg.java'
$keys = Join-Path $src 'gui\ClickGuiKeybind.java'
foreach ($check in @('autoBlockCheck', 'legitScaffoldCheck', 'killauraCheck')) {
    Assert-Contains $data $check "detector $check"
}
Assert-Omits $rain 'isMarked\(player' 'marked-player analysis gate'
Assert-Contains $rain 'ANTICHEAT\.retainPlayers' 'player retention'
Assert-Contains $rain 'onWorldUnload' 'world unload handler'
Assert-Contains $rain 'mc\.field_71441_e != lastWorld' 'world change reset'
Assert-Contains $alerts 'markedPlayers\.keySet\(\)\.retainAll' 'mark cleanup'
Assert-Contains $core 'if \(started\)' 'idempotent start guard'
Assert-Contains $core 'MinecraftForge\.EVENT_BUS\.register\(new Rain\(\)\)' 'tick registration'
Assert-Contains $core 'Rain\.ANTICHEAT\.clearAll\(\)' 'master off reset'
foreach ($key in @('masterEnabled', 'guiKey', 'toggleKey', 'detectAutoBlock', 'detectLegitScaffold', 'detectKillaura')) {
    Assert-Contains $config "props\.setProperty\(`"$key`"" "saved $key"
    Assert-Contains $config "props, `"$key`"" "loaded $key"
}
Assert-Contains $config 'guiKey = 54' 'default Right Shift code'
Assert-Contains $keys 'Keyboard\.KEY_RSHIFT' 'default Right Shift binding'
Assert-Contains $core 'cfg\.v\.guiKey = ClickGuiKeybind\.OPEN_GUI' 'configurable GUI key persistence'
$checks = Join-Path $src 'util\anticheat\checks'
foreach ($file in @('AutoBlockCheck.java', 'LegitScaffoldCheck.java', 'KillauraCheck.java')) {
    if (-not (Test-Path (Join-Path $checks $file))) { throw "missing $file" }
}
$unexpected = Get-ChildItem (Join-Path $src 'util\anticheat') -Recurse -Filter '*.java' |
    ForEach-Object { Select-String -Path $_.FullName -Pattern 'Lion|java\.net\.|ProcessBuilder|Runtime\.getRuntime\(|addToSendQueue|sendPacket' -Quiet } |
    Where-Object { $_ }
if ($unexpected) {
    throw 'unexpected Lion or detector network/process/send behavior'
}
Write-Host 'DETECTOR STATIC CHECKS OK'
