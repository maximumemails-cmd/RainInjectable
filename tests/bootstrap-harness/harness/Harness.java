import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Simulates what rain-payload.dll does inside a Forge JVM, without Minecraft:
 *   1. a "LaunchClassLoader" that can see the SRG-named MC + Forge jars
 *   2. a plain URLClassLoader(rain-runtime.jar, systemCL) holding RainBootstrap
 *   3. RainBootstrap.start(jar, dll)
 * The launch loader has a synthetic RainCore game-thread endpoint so success
 * and failure propagation can be verified without launching Minecraft.
 *
 * args: <mode: system|wrapper|failure> <rain-runtime.jar> <fakes dir> <core dir> <lib jar>...
 *   system  - fake Launch is on the system classpath (vanilla/Forge launcher case)
 *   wrapper - fake Launch is only in a child loader set as a thread's context
 *             classloader (Prism/MultiMC-style wrapper case)
 */
public class Harness {
   public static void main(String[] args) throws Exception {
      String mode = args[0];
      File jar = new File(args[1]);
      File fakes = new File(args[2]);
      if (mode.equals("path")) {
         File directory = new File(fakes.getParentFile(), "space and \u96e8");
         directory.mkdirs();
         File copied = new File(directory, "rain runtime.jar");
         java.nio.file.Files.copy(jar.toPath(), copied.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         jar = copied;
      }
      URL[] libs = new URL[args.length - 3];
      for (int i = 3; i < args.length; i++) {
         libs[i - 3] = new File(args[i]).toURI().toURL();
      }

      ClassLoader fakesLoader;
      if (!mode.equals("wrapper")) {
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
      if (mode.equals("failure")) {
         try {
            start.invoke(null, jar.getAbsolutePath(), "rain-payload.dll (harness)");
            throw new IllegalStateException("bootstrap incorrectly reported success");
         } catch (InvocationTargetException expected) {
            check("startup failure propagated", expected.getCause().toString().contains("synthetic game-thread failure"));
         }
         check("failed startup did not set loaded property", System.getProperty("rain.injected.loaded") == null);
         System.out.println("HARNESS OK (failure)");
         return;
      }
      start.invoke(null, jar.getAbsolutePath(), "rain-payload.dll (harness)");

      check("system property set", System.getProperty("rain.injected.loaded") != null);
      Class<?> core = Class.forName("first.rain.anticheat.RainCore", false, (ClassLoader) lcl);
      check("RainCore defined by launch loader", core.getClassLoader() == lcl);

      check("synthetic game-thread endpoint completed",
         core.getField("started").getBoolean(null));

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
