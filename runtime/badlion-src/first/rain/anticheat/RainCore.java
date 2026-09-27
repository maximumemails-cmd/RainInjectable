package first.rain.anticheat;

import first.rain.anticheat.config.cfg;
import first.rain.anticheat.gui.ClickGuiKeybind;
import net.minecraft.util.EnumChatFormatting;

/** Minimal control point for a game without Forge's event bus. */
public final class RainCore {
   private static volatile boolean started;

   private RainCore() {}

   public static synchronized void start() {
      if (started) return;
      cfg.load();
      ClickGuiKeybind.OPEN_GUI.func_151462_b(cfg.v.guiKey);
      ClickGuiKeybind.TOGGLE_RAIN.func_151462_b(cfg.v.toggleKey);
      started = true;
      System.out.println("[Rain] Badlion detector runtime started");
   }

   public static boolean isStarted() { return started; }
   public static String startMode() { return "badlion"; }
   public static boolean isEnabled() { return cfg.v.masterEnabled; }

   public static void setEnabled(boolean enabled, boolean announce) {
      cfg.v.masterEnabled = enabled;
      if (!enabled) Rain.ANTICHEAT.clearAll();
      cfg.save();
      if (announce) Rain.addMessage(enabled ? EnumChatFormatting.GREEN + "Rain enabled"
         : EnumChatFormatting.RED + "Rain disabled");
   }

   public static void toggle() { setEnabled(!isEnabled(), true); }

   public static void saveKeybinds() {
      cfg.v.guiKey = ClickGuiKeybind.OPEN_GUI.func_151463_i();
      cfg.v.toggleKey = ClickGuiKeybind.TOGGLE_RAIN.func_151463_i();
      cfg.save();
   }
}
