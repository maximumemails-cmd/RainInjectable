package first.rain.anticheat.badlion;

import first.rain.anticheat.Rain;
import first.rain.anticheat.RainCore;
import first.rain.anticheat.gui.ClickGui;
import first.rain.anticheat.gui.ClickGuiKeybind;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Schedules Rain's observation loop on Badlion's Minecraft thread. */
public final class BadlionBootstrap {
   private static final AtomicBoolean pending = new AtomicBoolean();
   private static volatile boolean running;
   private static volatile long heartbeats;
   private static volatile long guiOpenCount;
   private static volatile boolean guiReady;
   private static volatile boolean guiOpen;
   private static volatile String lastError = "";
   private static boolean lastGuiKeyDown;
   private static boolean lastToggleKeyDown;
   private static boolean lastGuiScreenOpen;

   private BadlionBootstrap() {}

   public static synchronized void start(String jarPath) throws Exception {
      if (running) return;
      Minecraft mc = null;
      long deadline = System.currentTimeMillis() + 20000L;
      while (System.currentTimeMillis() < deadline) {
         mc = Minecraft.func_71410_x();
         if (mc != null && mc.field_71474_y != null) break;
         Thread.sleep(100L);
      }
      if (mc == null || mc.field_71474_y == null) {
         throw new IllegalStateException("Badlion Minecraft instance was not ready");
      }

      // This is the Notch name of 1.8.9 Minecraft.addScheduledTask(Runnable).
      // It is verified against the pinned 1.8.9 mapping during the build.
      final Method schedule = Minecraft.class.getMethod("a", Runnable.class);
      final Minecraft game = mc;
      final File statusFile = new File(new File(jarPath).getAbsoluteFile().getParentFile(),
         "rain-badlion-status-" + processId() + ".txt");
      Future<?> init = (Future<?>)schedule.invoke(game, new Runnable() {
         @Override public void run() {
            RainCore.start();
            // Resolve every GUI dependency before native injection reports success.
            new ClickGui();
            guiReady = true;
         }
      });
      init.get(10, TimeUnit.SECONDS);
      if (!RainCore.isStarted() || !guiReady) throw new IllegalStateException("Rain GUI initialization failed");
      running = true;

      Thread ticker = new Thread(new Runnable() {
         @Override public void run() {
            int cycles = 0;
            while (running) {
               try {
                  if (pending.compareAndSet(false, true)) {
                     schedule.invoke(game, new Runnable() {
                        @Override public void run() {
                           try {
                              Rain.tick();
                              pollKeys(game);
                              heartbeats++;
                           }
                           catch (Throwable t) {
                              lastError = t.toString();
                              System.err.println("[Rain] Badlion tick failed: " + t);
                           }
                           finally { pending.set(false); }
                        }
                     });
                  }
                  if (++cycles % 20 == 0) writeStatus(statusFile);
                  Thread.sleep(50L);
               } catch (Throwable t) {
                  running = false;
                  lastError = t.toString();
                  writeStatus(statusFile);
                  System.err.println("[Rain] Badlion scheduler stopped: " + t);
               }
            }
         }
      }, "Rain-Badlion-Detector");
      ticker.setDaemon(true);
      ticker.start();
      long heartbeatDeadline = System.currentTimeMillis() + 5000L;
      while (heartbeats == 0 && System.currentTimeMillis() < heartbeatDeadline) Thread.sleep(50L);
      if (heartbeats == 0) {
         running = false;
         throw new IllegalStateException("Badlion game-thread detector loop did not run");
      }
      writeStatus(statusFile);
   }

   private static String processId() {
      String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
      int at = name.indexOf('@');
      return at > 0 ? name.substring(0, at) : "unknown";
   }

   private static void pollKeys(Minecraft game) {
      boolean screenOpen = game.field_71462_r instanceof ClickGui;
      int guiKey = ClickGuiKeybind.OPEN_GUI.func_151463_i();
      int toggleKey = ClickGuiKeybind.TOGGLE_RAIN.func_151463_i();
      boolean guiDown = keyDown(guiKey);
      boolean toggleDown = keyDown(toggleKey);

      if (toggleDown && !lastToggleKeyDown && game.field_71462_r == null) RainCore.toggle();
      if (guiDown && !lastGuiKeyDown && !lastGuiScreenOpen && game.field_71462_r == null) {
         game.func_147108_a(new ClickGui());
         screenOpen = game.field_71462_r instanceof ClickGui;
         if (screenOpen) guiOpenCount++;
      }
      // A key handled by ClickGui may have closed it earlier in this game tick.
      // Treat that key as held until release so the poll does not reopen it.
      lastGuiKeyDown = guiDown;
      lastToggleKeyDown = toggleDown;
      lastGuiScreenOpen = screenOpen;
      guiOpen = screenOpen;
   }

   private static boolean keyDown(int code) {
      return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isCreated() && Keyboard.isKeyDown(code);
   }

   private static void writeStatus(File file) {
      try (FileWriter writer = new FileWriter(file, false)) {
         writer.write("running=" + running + "\n");
         writer.write("heartbeats=" + heartbeats + "\n");
         writer.write("guiReady=" + guiReady + "\n");
         writer.write("guiOpen=" + guiOpen + "\n");
         writer.write("guiOpenCount=" + guiOpenCount + "\n");
         writer.write("inWorld=" + Rain.inWorld + "\n");
         writer.write("eligiblePlayers=" + Rain.eligiblePlayers + "\n");
         writer.write("processedTicks=" + Rain.processedTicks + "\n");
         writer.write("lastError=" + lastError + "\n");
      } catch (Throwable ignored) {}
   }
}
