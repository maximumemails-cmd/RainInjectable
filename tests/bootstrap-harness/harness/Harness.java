import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Simulates what rain-payload.dll does inside a Forge JVM, without Minecraft:
 *   1. a "LaunchClassLoader" that can see the SRG-named MC + Forge jars
 *   2. a plain URLClassLoader(rain-runtime.jar, systemCL) holding RainBootstrap
 *   3. RainBootstrap.start(jar, dll)
 * Then asserts the jar was added to the launch loader, RainCore was defined by
 * it, and the injected-start thread was spawned.
 *
 * args: <mode: system|wrapper> <rain-runtime.jar> <fakes classes dir> <lib jar>...
 *   system  - fake Launch is on the system classpath (vanilla/Forge launcher case)
 *   wrapper - fake Launch is only in a child loader set as a thread's context
 *             classloader (Prism/MultiMC-style wrapper case)
 */
public class Harness {
   public static void main(String[] args) throws Exception {
      String mode = args[0];
      File jar = new File(args[1]);
      File fakes = new File(args[2]);
      URL[] libs = new URL[args.length - 3];
      for (int i = 3; i < args.length; i++) {
         libs[i - 3] = new File(args[i]).toURI().toURL();
      }

      ClassLoader fakesLoader;
      if (mode.equals("system")) {
         fakesLoader = ClassLoader.getSystemClassLoader();
         Class.forName("net.minecraft.launchwrapper.Launch", false, fakesLoader);
      } else {
         fakesLoader = new URLClassLoader(new URL[] { fakes.toURI().toURL() }, null);
         try {
            Class.forName("net.minecraft.launchwrapper.Launch", false, ClassLoader.getSystemClassLoader());
            throw new IllegalStateException("wrapper mode requires Launch NOT on the system classpath");
         } catch (ClassNotFoundException expected) {
         }
      }

      Class<?> lclClass = Class.forName("net.minecraft.launchwrapper.LaunchClassLoader", true, fakesLoader);
      Object lcl = lclClass.getConstructor(URL[].class, ClassLoader.class).newInstance(libs, fakesLoader);
      Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch", true, fakesLoader);
      launch.getField("classLoader").set(null, lcl);

      if (mode.equals("wrapper")) {
         Thread holder = new Thread(new Runnable() {
            public void run() {
               try { Thread.sleep(60000); } catch (InterruptedException ignored) {}
            }
         }, "Client thread (fake)");
         holder.setDaemon(true);
         holder.setContextClassLoader((ClassLoader) lcl);
         holder.start();
      }

      URLClassLoader payloadLoader = new URLClassLoader(new URL[] { jar.toURI().toURL() }, ClassLoader.getSystemClassLoader());
      Class<?> bootstrap = Class.forName("first.rain.anticheat.bootstrap.RainBootstrap", true, payloadLoader);
      Method start = bootstrap.getMethod("start", String.class, String.class);
      start.invoke(null, jar.getAbsolutePath(), "rain-payload.dll (harness)");

      check("system property set", System.getProperty("rain.injected.loaded") != null);
      Class<?> core = Class.forName("first.rain.anticheat.RainCore", false, (ClassLoader) lcl);
      check("RainCore defined by launch loader", core.getClassLoader() == lcl);

      Thread.sleep(500);
      boolean threadFound = false;
      for (Thread t : Thread.getAllStackTraces().keySet()) {
         if (t.getName().equals("Rain-Injected-Start")) threadFound = true;
      }
      check("Rain-Injected-Start thread spawned", threadFound);

      start.invoke(null, jar.getAbsolutePath(), "second call");
      check("second start is a no-op", true);

      System.out.println("HARNESS OK (" + mode + ")");
      System.exit(0);
   }

   private static void check(String what, boolean ok) {
      System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
      if (!ok) {
         System.exit(1);
      }
   }
}
