package first.rain.anticheat.util.anticheat;

/** The HUD adapter and ClickGui render the shared effect on the game thread. */
public final class FlashNotification {
   private FlashNotification() {}
   public static void trigger() { FlashEffect.trigger(); }
   public static void test() { FlashEffect.test(); }
}
