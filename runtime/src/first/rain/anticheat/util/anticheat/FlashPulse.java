package first.rain.anticheat.util.anticheat;

/** Time-based envelope shared by HUD rendering and the settings preview. */
public final class FlashPulse {
   public static final long DURATION_MS = 1200L;
   private long started = -1L;
   private boolean preview;

   public void start(long now, boolean preview) { this.started = now; this.preview = preview; }
   public void clear() { started = -1L; }
   public int color(long now, boolean enabled, int opacity, int rgb) {
      long elapsed = now - started;
      if (started < 0L) return 0;
      if (elapsed < 0L || elapsed >= DURATION_MS || (!preview && !enabled)) {
         clear();
         return 0;
      }
      float progress = (float)elapsed / DURATION_MS;
      float pulse = 0.55F + 0.45F * (float)Math.cos(progress * Math.PI * 4.0D);
      float alpha = Math.max(0, Math.min(100, opacity)) / 100.0F * pulse * (1.0F - progress);
      return ((int)(alpha * 255.0F) << 24) | (rgb & 0xFFFFFF);
   }
}
