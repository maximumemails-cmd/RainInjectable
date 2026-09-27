package first.rain.anticheat;

/** Synthetic game-thread endpoint for the classloader smoke harness only. */
public final class RainCore {
   public static boolean started;

   public static void startInjected(String jarPath) {
      if (Boolean.getBoolean("rain.harness.fail")) {
         throw new IllegalStateException("synthetic game-thread failure");
      }
      started = true;
   }
}
