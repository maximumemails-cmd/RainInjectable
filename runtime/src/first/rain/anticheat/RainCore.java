package first.rain.anticheat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.gui.ClickGuiKeybind;
import first.rain.anticheat.util.anticheat.FlashNotification;
import first.rain.anticheat.util.anticheat.NametagOverlayRenderer;

/**
 * Central startup/control point. Rain can be started either as a normal Forge
 * mod (Rain.init -> RainCore.start("mod")) or by the runtime injector
 * (RainBootstrap -> RainCore.startInjected). Also owns the master
 * enable/disable toggle and keybind persistence.
 */
public final class RainCore {
   public static final String VERSION = "1.2.0-injectable";

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

   /** Wait for the game thread to finish registration before reporting success. */
   public static void startInjected(final String jarPath) throws Exception {
      long deadline = System.currentTimeMillis() + 20000L;
      Minecraft mc = null;
      while (System.currentTimeMillis() < deadline) {
         mc = Minecraft.func_71410_x();
         if (mc != null && mc.field_71474_y != null) break;
         Thread.sleep(200L);
      }
      if (mc == null || mc.field_71474_y == null) {
         throw new IllegalStateException("Minecraft was not ready after 20 seconds");
      }
      Runnable task = new Runnable() {
         @Override
         public void run() {
            boolean fresh = start("injected");
            if (fresh) {
               Rain.addMessage(EnumChatFormatting.AQUA + "Rain " + VERSION + " injected. "
                  + EnumChatFormatting.GRAY + "Press the GUI key (default RSHIFT) to open settings.");
            }
         }
      };
      // The game API returns a Guava ListenableFuture, which implements Future.
      Method addScheduledTask = Minecraft.class.getMethod("func_152344_a", Runnable.class);
      Future<?> completion = (Future<?>) addScheduledTask.invoke(mc, task);
      completion.get(10, TimeUnit.SECONDS);
      if (!isStarted()) throw new IllegalStateException("Rain registration did not complete");
   }
}
