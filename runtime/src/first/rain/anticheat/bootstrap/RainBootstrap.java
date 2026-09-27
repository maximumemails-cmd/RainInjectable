package first.rain.anticheat.bootstrap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Forge-independent bootstrap entry point. Loaded by RainInjector's native
 * payload through a plain URLClassLoader that has no access to Forge's
 * classloader, so this class must not reference any Minecraft/Forge/LWJGL or
 * other first.rain.* classes at compile time — everything is reflection.
 */
public final class RainBootstrap {
   public static final String VERSION = "1.2.0-injectable";

   private static String logFile;

   private RainBootstrap() {
   }

   public static final class TargetInfo {
      public ClassLoader gameClassLoader;
      public String clientName = "Unknown";
      public String javaVersion = System.getProperty("java.version");
      public boolean hasLaunchWrapper;
      public boolean hasForge;
      public boolean hasFabric;
      public boolean hasMcpMinecraft;
      public boolean hasNotchMinecraft;
      public String minecraftVersion = "1.8.9";
   }

   /** Native payload entry point. */
   public static void start(String jarPath, String dllPath) {
      try {
         File jar = new File(jarPath);
         File parent = jar.getAbsoluteFile().getParentFile();
         logFile = parent != null ? new File(parent, "rain-bootstrap.log").getAbsolutePath() : "rain-bootstrap.log";
         log("RainBootstrap start: jar=" + jarPath + " dll=" + dllPath);

         synchronized (RainBootstrap.class) {
            if (System.getProperty("rain.injected.loaded") != null) {
               log("already loaded, ignoring");
               return;
            }

            if (!jar.isFile()) {
               throw new IllegalStateException("jar not found: " + jarPath);
            }

            TargetInfo target = inspectTargetEnvironment();
            log("Target environment resolved:");
            log("  Client: " + target.clientName);
            log("  Java: " + target.javaVersion + " (" + System.getProperty("java.vm.name") + ")");
            log("  ClassLoader: " + (target.gameClassLoader != null ? target.gameClassLoader.getClass().getName() : "none"));
            log("  LaunchWrapper: " + target.hasLaunchWrapper);
            log("  Forge mod framework: " + target.hasForge);
            log("  Fabric mod framework: " + target.hasFabric);
            log("  Minecraft classes: MCP=" + target.hasMcpMinecraft + " Notch=" + target.hasNotchMinecraft);

            if (target.clientName.contains("Badlion") && target.hasNotchMinecraft && !target.hasForge) {
               File badlionJar = new File(parent, "rain-badlion.jar");
               if (!badlionJar.isFile()) {
                  throw new IllegalStateException("BADLION_RUNTIME_MISSING: " + badlionJar.getAbsolutePath());
               }
               ClassLoader badlionLoader = new URLClassLoader(
                  new URL[]{ badlionJar.toURI().toURL() }, target.gameClassLoader);
               Class<?> badlionBootstrap = Class.forName(
                  "first.rain.anticheat.badlion.BadlionBootstrap", true, badlionLoader);
               badlionBootstrap.getMethod("start", String.class).invoke(null, badlionJar.getAbsolutePath());
               System.setProperty("rain.injected.loaded", badlionJar.getAbsolutePath());
               log("Badlion runtime, GUI classes and game-thread heartbeat confirmed");
               return;
            }

            if (!target.hasForge) {
               String reason;
               if (target.clientName.contains("Badlion")) {
                  reason = "Target runtime is Badlion Client (" + target.minecraftVersion + ", Java " + target.javaVersion
                     + "). Badlion uses a custom client runtime with Notch mappings and does not include the Forge 1.8.9 mod framework required by Rain.";
               } else if (target.clientName.contains("Lunar")) {
                  reason = "Target runtime is Lunar Client (" + target.minecraftVersion + ", Java " + target.javaVersion
                     + "). Lunar uses a custom client runtime and does not include the Forge 1.8.9 mod framework required by Rain.";
               } else if (target.hasFabric) {
                  reason = "Target runtime is Fabric loader. Rain requires Forge 1.8.9.";
               } else {
                  reason = "Target JVM is running " + target.clientName + " (" + target.minecraftVersion
                     + "), but the Forge 1.8.9 mod framework is not detected. Rain requires Forge 1.8.9.";
               }
               log("Incompatible environment: " + reason);
               throw new IllegalStateException("FORGE_FRAMEWORK_MISSING: " + reason);
            }

            if (target.gameClassLoader == null) {
               throw new IllegalStateException("CLASSLOADER_NOT_FOUND: Could not identify a usable game ClassLoader in target JVM.");
            }

            ClassLoader targetLoader = target.gameClassLoader;
            boolean urlAdded = false;
            try {
               Method addUrl = targetLoader.getClass().getMethod("addURL", URL.class);
               addUrl.setAccessible(true);
               addUrl.invoke(targetLoader, jar.toURI().toURL());
               urlAdded = true;
               log("added jar to Launch/game classloader: " + jarPath);
            } catch (Throwable t) {
               log("Target classloader does not support addURL (" + t.getMessage() + "), using child URLClassLoader");
            }

            ClassLoader executionLoader = urlAdded
               ? targetLoader
               : new URLClassLoader(new URL[]{ jar.toURI().toURL() }, targetLoader);

            Class<?> core = Class.forName("first.rain.anticheat.RainCore", true, executionLoader);
            core.getMethod("startInjected", String.class).invoke(null, jarPath);
            log("RainCore.startInjected invoked");

            System.setProperty("rain.injected.loaded", jarPath);
            log("bootstrap complete");
         }
      } catch (Throwable t) {
         StringWriter sw = new StringWriter();
         t.printStackTrace(new PrintWriter(sw));
         log(sw.toString());
         Throwable cause = t instanceof InvocationTargetException
            ? ((InvocationTargetException)t).getTargetException() : t;
         throw new IllegalStateException("Rain bootstrap: " + cause.toString(), cause);
      }
   }

   /**
    * Discovers the game classloader, active threads, and installed mod frameworks.
    */
   private static TargetInfo inspectTargetEnvironment() {
      TargetInfo info = new TargetInfo();

      // Check LaunchWrapper first (standard Forge 1.8.9 layout)
      try {
         Class<?> launchClass = Class.forName("net.minecraft.launchwrapper.Launch", false, ClassLoader.getSystemClassLoader());
         Object lcl = launchClass.getField("classLoader").get(null);
         if (lcl instanceof ClassLoader) {
            info.hasLaunchWrapper = true;
            info.gameClassLoader = (ClassLoader)lcl;
            log("Detected LaunchWrapper: Launch.classLoader is active");
         } else {
            log("Launch class present on classpath, but Launch.classLoader is null");
         }
      } catch (Throwable ignored) {
      }

      // Scan live threads to find game context classloaders
      Set<ClassLoader> candidates = new HashSet<ClassLoader>();
      if (info.gameClassLoader != null) {
         candidates.add(info.gameClassLoader);
      }
      candidates.add(ClassLoader.getSystemClassLoader());

      Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
      for (Thread t : traces.keySet()) {
         String name = t.getName();
         ClassLoader cl = t.getContextClassLoader();
         if (cl != null) candidates.add(cl);

         if (name.equalsIgnoreCase("Client thread") || name.equalsIgnoreCase("Minecraft") || name.equalsIgnoreCase("main")) {
            if (cl != null && info.gameClassLoader == null) {
               info.gameClassLoader = cl;
            }
         }
      }

      // Check client branding / process markers
      String cmd = "";
      try {
         cmd = System.getProperty("sun.java.command", "");
      } catch (Throwable ignored) {
      }
      String userDir = System.getProperty("user.dir", "");

      // Identify client kind
      if (containsIgnoreCase(cmd, "badlion") || containsIgnoreCase(userDir, "badlion") || hasClass("net.badlion.odin.OdinNative", candidates)) {
         info.clientName = "Badlion Client";
         info.minecraftVersion = "1.8.9";
      } else if (containsIgnoreCase(cmd, "lunarclient") || containsIgnoreCase(cmd, "moonsworth") || hasClass("com.moonsworth.lunar.genesis.Genesis", candidates)) {
         info.clientName = "Lunar Client";
         info.minecraftVersion = "1.8.9";
      } else if (containsIgnoreCase(cmd, "feather") || hasClass("net.digitalingot.feather.FeatherClient", candidates)) {
         info.clientName = "Feather Client";
      } else if (info.hasLaunchWrapper) {
         info.clientName = "Minecraft Forge 1.8.9";
         info.minecraftVersion = "1.8.9";
      } else {
         info.clientName = "Minecraft (Vanilla / Custom)";
      }

      // Rain must resolve Minecraft, Forge's event bus, and ForgeVersion from
      // one loader. Seeing those classes in unrelated loaders cannot run Rain.
      ClassLoader compatibleLoader = null;
      for (ClassLoader cl : candidates) {
         boolean hasMinecraft = tryLoad(cl, "net.minecraft.client.Minecraft");
         boolean hasRealForge = tryLoad(cl, "net.minecraftforge.common.MinecraftForge")
            && tryLoad(cl, "net.minecraftforge.common.ForgeVersion");
         if (hasMinecraft) {
            info.hasMcpMinecraft = true;
         }
         if (hasRealForge && hasMinecraft && compatibleLoader == null) {
            compatibleLoader = cl;
            info.hasForge = true;
         }
         if (tryLoad(cl, "ave")) {
            info.hasNotchMinecraft = true;
         }
         if (tryLoad(cl, "net.fabricmc.loader.api.FabricLoader")) {
            info.hasFabric = true;
         }
      }

      // The execution loader must be able to load the deobfuscated Minecraft
      // classes Rain links against. Under LaunchWrapper (Forge) that is
      // Launch.classLoader; a parent such as AppClassLoader may resolve
      // net.minecraftforge.common.MinecraftForge yet be unable to load
      // net.minecraft.client.Minecraft, so never downgrade to it.
      if (compatibleLoader != null) {
         info.gameClassLoader = compatibleLoader;
      } else if (info.gameClassLoader == null) {
         info.gameClassLoader = ClassLoader.getSystemClassLoader();
      }

      return info;
   }

   private static boolean hasClass(String name, Set<ClassLoader> loaders) {
      for (ClassLoader cl : loaders) {
         if (tryLoad(cl, name)) return true;
      }
      return false;
   }

   private static boolean tryLoad(ClassLoader cl, String name) {
      if (cl == null) return false;
      try {
         Class.forName(name, false, cl);
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   private static boolean containsIgnoreCase(String haystack, String needle) {
      if (haystack == null || needle == null) return false;
      return haystack.toLowerCase().contains(needle.toLowerCase());
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
