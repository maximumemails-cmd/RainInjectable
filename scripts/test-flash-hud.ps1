$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'build\flash-harness'
$jdk = $env:RAIN_JDK8
if (-not $jdk) {
    $jdk = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory |
        Where-Object { $_.Name -match '^jdk-?8' } | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) { throw 'JDK 8 not found' }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = New-Object 'System.Collections.Generic.List[string]'
function Write-Java($relative, $body) {
    $path = Join-Path $out $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    [IO.File]::WriteAllText($path, $body, (New-Object Text.UTF8Encoding $false))
    $sources.Add($path)
}
foreach ($name in @('ScaledResolution', 'FontRenderer', 'GuiNewChat', 'GuiSpectator', 'GuiPlayerTabOverlay')) {
    Write-Java "net/minecraft/client/gui/$name.java" "package net.minecraft.client.gui; public class $name {}"
}
Write-Java 'net/minecraft/util/IChatComponent.java' 'package net.minecraft.util; public interface IChatComponent {}'
Write-Java 'net/minecraft/client/Minecraft.java' @'
package net.minecraft.client;
import net.minecraft.client.gui.GuiIngame;
public class Minecraft { public GuiIngame field_71456_v; public Object field_71462_r; }
'@
Write-Java 'first/rain/anticheat/gui/ClickGui.java' 'package first.rain.anticheat.gui; public class ClickGui {}'
Write-Java 'first/rain/anticheat/util/anticheat/FlashEffect.java' @'
package first.rain.anticheat.util.anticheat;
public class FlashEffect { public static int renders; public static void render() { renders++; } }
'@
Write-Java 'net/minecraft/client/gui/GuiIngame.java' @'
package net.minecraft.client.gui;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;
public class GuiIngame {
   public float field_73843_a = 1;
   public int calls;
   public String last;
   public GuiIngame(Minecraft m) { func_175177_a(); }
   private void call(String s) { calls++; last = s; }
   public void func_175177_a() { call("func_175177_a"); }
   public void func_175180_a(float f) { call("func_175180_a"); field_73843_a += f; }
   public void func_73831_a() { call("func_73831_a"); field_73843_a++; }
   public void func_175186_a(ScaledResolution r, int x) { call("func_175186_a"); }
   public void func_175176_b(ScaledResolution r, int x) { call("func_175176_b"); }
   public void func_181551_a(ScaledResolution r) { call("func_181551_a"); }
   public void func_175185_b(ScaledResolution r) { call("func_175185_b"); }
   public void func_180478_c(ScaledResolution r) { call("func_180478_c"); }
   public void func_73833_a(String s) { call("func_73833_a"); }
   public void func_110326_a(String s, boolean b) { call("func_110326_a"); }
   public void func_175178_a(String a, String b, int c, int d, int e) { call("func_175178_a"); }
   public void func_175188_a(IChatComponent c, boolean b) { call("func_175188_a"); }
   public GuiNewChat func_146158_b() { call("func_146158_b"); return chat; }
   public int func_73834_c() { call("func_73834_c"); return 42; }
   public FontRenderer func_175179_f() { call("func_175179_f"); return font; }
   public GuiSpectator func_175187_g() { call("func_175187_g"); return spectator; }
   public GuiPlayerTabOverlay func_175181_h() { call("func_175181_h"); return tab; }
   public void func_181029_i() { call("func_181029_i"); }
   public final GuiNewChat chat = new GuiNewChat();
   public final FontRenderer font = new FontRenderer();
   public final GuiSpectator spectator = new GuiSpectator();
   public final GuiPlayerTabOverlay tab = new GuiPlayerTabOverlay();
}
'@
$sources.Add((Join-Path $root 'runtime\badlion-src\first\rain\anticheat\badlion\FlashHud.java'))
$sources.Add((Join-Path $root 'tests\flash-harness\HudHarness.java'))
& (Join-Path $jdk 'bin\javac.exe') -source 1.8 -target 1.8 -Xlint:-options -d $out $sources.ToArray()
if ($LASTEXITCODE -ne 0) { throw 'Flash HUD harness compilation failed' }
& (Join-Path $jdk 'bin\java.exe') -cp $out HudHarness
if ($LASTEXITCODE -ne 0) { throw 'Flash HUD harness failed' }
