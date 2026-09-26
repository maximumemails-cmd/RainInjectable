package first.rain.anticheat.bootstrap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Forge-independent bootstrap entry point. Loaded by RainInjector's native
 * payload through a plain URLClassLoader that has no access to Forge's
 * classloader, so this class must not reference any Minecraft/Forge/LWJGL or
 * other first.rain.* classes at compile time — everything is reflection.
 */
public final class RainBootstrap {
   public static final String VERSION = "1.0.0-injectable";

   private static String logFile;

   private RainBootstrap() {
   }

   /** Native payload entry point. */
   public static void start(String jarPath, String dllPath) {
      try {
         File jar = new File(jarPath);
         File parent = jar.getAbsoluteFile().getParentFile();
         logFile = parent != null ? new File(parent, "rain-bootstrap.log").getAbsolutePath() : "rain-bootstrap.log";
         log("RainBootstrap start jar=" + jarPath + " dll=" + dllPath);

         synchronized (RainBootstrap.class) {
            if (System.getProperty("rain.injected.loaded") != null) {
               log("already loaded, ignoring");
               return;
            }

            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", false, ClassLoader.getSystemClassLoader());
            Object lcl = launch.getField("classLoader").get(null);
            if (lcl == null) {
               throw new IllegalStateException("Launch.classLoader is null - not a LaunchWrapper (Forge) process");
            }

            try {
               Class.forName("net.minecraftforge.common.MinecraftForge", false, (ClassLoader)lcl);
            } catch (ClassNotFoundException e) {
               throw new IllegalStateException("Forge not detected in target - Rain requires Forge 1.8.9", e);
            }

            if (!jar.isFile()) {
               throw new IllegalStateException("jar not found: " + jarPath);
            }
            Method addUrl = lcl.getClass().getMethod("addURL", URL.class);
            addUrl.invoke(lcl, jar.toURI().toURL());
            log("added jar to Launch classloader: " + jarPath);

            Class<?> core = Class.forName("first.rain.anticheat.RainCore", true, (ClassLoader)lcl);
            core.getMethod("startInjected", String.class).invoke(null, jarPath);
            log("RainCore.startInjected invoked");

            System.setProperty("rain.injected.loaded", jarPath);
            log("bootstrap complete");
         }
      } catch (Throwable t) {
         StringWriter sw = new StringWriter();
         t.printStackTrace(new PrintWriter(sw));
         log(sw.toString());
         throw new RuntimeException(t);
      }
   }

   /** Harmless helper for static verification. */
   public static void main(String[] args) {
      System.out.println("RainBootstrap " + VERSION + " - this jar is loaded by RainInjector, not run directly.");
   }

   private static void log(String line) {
      String full = "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "] " + line;
      System.out.println(full);
      if (logFile != null) {
         try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(full + System.lineSeparator());
         } catch (IOException ignored) {
         }
      }
   }
}
