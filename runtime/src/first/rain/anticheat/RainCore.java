package first.rain.anticheat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import java.lang.reflect.Method;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.gui.ClickGuiKeybind;
import first.rain.anticheat.util.anticheat.AlertManager;
import first.rain.anticheat.util.anticheat.AntiCheatData;
import first.rain.anticheat.util.anticheat.FlashNotification;
import first.rain.anticheat.util.anticheat.NametagOverlayRenderer;

/**
 * Central startup/control point. Rain can be started either as a normal Forge
 * mod (Rain.init -> RainCore.start("mod")) or by the runtime injector
 * (RainBootstrap -> RainCore.startInjected). Also owns the master
 * enable/disable toggle and keybind persistence.
 */
public final class RainCore {
   public static final String VERSION = "1.0.0-injectable";

   private static volatile boolean started;
   private static String startMode;

   private RainCore() {
   }

   /** Starts the runtime once. Returns false if already started. */
   public static synchronized boolean start(String mode) {
      if (started) {
         return false;
      }
      ClickGuiKeybind.OPEN_GUI.func_151462_b(cfg.v.guiKey); // setKeyCode
      ClickGuiKeybind.TOGGLE_RAIN.func_151462_b(cfg.v.toggleKey);
      ClientRegistry.registerKeyBinding(ClickGuiKeybind.OPEN_GUI);
      ClientRegistry.registerKeyBinding(ClickGuiKeybind.TOGGLE_RAIN);
      MinecraftForge.EVENT_BUS.register(new Rain());
      MinecraftForge.EVENT_BUS.register(new ClickGuiKeybind());
      MinecraftForge.EVENT_BUS.register(new FlashNotification());
      MinecraftForge.EVENT_BUS.register(new NametagOverlayRenderer());
      started = true;
      startMode = mode;
      System.out.println("[Rain] core started (mode=" + mode + ", version=" + VERSION + ")");
      return true;
   }

   public static boolean isStarted() {
      return started;
   }

   public static String startMode() {
      return startMode;
   }

   public static boolean isEnabled() {
      return cfg.v.masterEnabled;
   }

   public static void setEnabled(boolean enabled, boolean announce) {
      cfg.v.masterEnabled = enabled;
      if (!enabled) {
         Rain.ANTICHEAT.clearAll();
         AlertManager.clear();
      }
      cfg.save();
      if (announce) {
         Rain.addMessage(enabled ? EnumChatFormatting.GREEN + "Rain enabled" : EnumChatFormatting.RED + "Rain disabled");
      }
   }

   public static void toggle() {
      setEnabled(!isEnabled(), true);
   }

   public static void saveKeybinds() {
      cfg.v.guiKey = ClickGuiKeybind.OPEN_GUI.func_151463_i(); // getKeyCode
      cfg.v.toggleKey = ClickGuiKeybind.TOGGLE_RAIN.func_151463_i();
      cfg.save();
   }

   /**
    * Entry point used by the injector bootstrap (via reflection). Safe to call
    * from a non-game thread: waits for Minecraft to finish initializing, then
    * hands the actual start to the game thread via addScheduledTask.
    */
   public static void startInjected(final String jarPath) {
      try {
         Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
               try {
                  long deadline = System.currentTimeMillis() + 5L * 60L * 1000L;
                  Minecraft mc = null;
                  while (System.currentTimeMillis() < deadline) {
                     try {
                        mc = Minecraft.func_71410_x();
                        if (mc != null && mc.field_71474_y != null) {
                           break;
                        }
                     } catch (Throwable ignored) {
                     }
                     try {
                        Thread.sleep(250L);
                     } catch (InterruptedException ignored) {
                     }
                  }
                  if (mc == null || mc.field_71474_y == null) {
                     System.err.println("[Rain] startInjected: timed out after 5 minutes waiting for Minecraft to initialize");
                     return;
                  }
                  final Minecraft fMc = mc;
                  Runnable task = new Runnable() {
                     @Override
                     public void run() {
                        boolean fresh = start("injected");
                        if (fresh) {
                           Rain.addMessage(EnumChatFormatting.AQUA + "Rain " + VERSION + " injected. "
                              + EnumChatFormatting.GRAY + "Press the GUI key (default RSHIFT) to open settings.");
                        } else {
                           Rain.addMessage(EnumChatFormatting.GRAY + "Rain is already running.");
                        }
                     }
                  };
                  try {
                     // func_152344_a returns ListenableFuture (Guava), which is not on the
                     // compile classpath, so invoke it reflectively.
                     Method addScheduledTask = Minecraft.class.getMethod("func_152344_a", Runnable.class);
                     addScheduledTask.invoke(fMc, task);
                  } catch (Exception e) {
                     e.printStackTrace(System.err);
                  }
               } catch (Throwable t) {
                  t.printStackTrace(System.err);
               }
            }
         }, "Rain-Injected-Start");
         thread.setDaemon(true);
         thread.start();
      } catch (Throwable t) {
         t.printStackTrace(System.err);
      }
   }
}
