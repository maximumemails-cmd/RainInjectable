package first.rain.anticheat.util.anticheat.checks;

/** Crouch and swing timing with a short event-order window. Context means
 * block held, grounded, downward pitch, and bridge-like movement. */
public final class ScaffoldEvidence {
   private final EpisodeEvidence evidence = new EpisodeEvidence(26, 5, 20);
   private boolean wasSneaking;
   private boolean wasSwinging;
   private long crouchStart = Long.MIN_VALUE;
   private long lastSwing = Long.MIN_VALUE;
   private long lastEnd = Long.MIN_VALUE;
   private int lastDuration = -1;
   private int regularEpisodes;
   private long pendingEnd = Long.MIN_VALUE;
   private boolean pendingContext;
   private long lastTick = Long.MIN_VALUE;

   public boolean observe(long tick, boolean sneak, boolean swing, boolean context) {
      if (lastTick != Long.MIN_VALUE && (tick <= lastTick || tick - lastTick > 2)) {
         evidence.reset();
         wasSneaking = false;
         wasSwinging = false;
         crouchStart = Long.MIN_VALUE;
         lastSwing = Long.MIN_VALUE;
         lastEnd = Long.MIN_VALUE;
         lastDuration = -1;
         regularEpisodes = 0;
         pendingEnd = Long.MIN_VALUE;
      }
      lastTick = tick;
      if (swing && !wasSwinging) lastSwing = tick;
      if (sneak && !wasSneaking) crouchStart = tick;
      boolean episode = false;
      if (!sneak && wasSneaking && crouchStart != Long.MIN_VALUE) {
         long duration = tick - crouchStart;
         long cadence = lastEnd == Long.MIN_VALUE ? Long.MAX_VALUE : tick - lastEnd;
         lastEnd = tick;
         if (context && duration >= 1 && duration <= 2 && cadence >= 3 && cadence <= 12) {
            regularEpisodes = duration == lastDuration ? regularEpisodes + 1 : 1;
            pendingContext = regularEpisodes >= 3;
            pendingEnd = tick;
         } else {
            regularEpisodes = 0;
            pendingEnd = Long.MIN_VALUE;
         }
         lastDuration = (int)duration;
      }
      if (pendingEnd != Long.MIN_VALUE) {
         if (tick - pendingEnd > 2) {
            pendingEnd = Long.MIN_VALUE;
         } else if (lastSwing >= pendingEnd - 1 && lastSwing <= tick) {
            episode = pendingContext;
            pendingEnd = Long.MIN_VALUE;
         }
      }
      wasSneaking = sneak;
      wasSwinging = swing;
      return evidence.observe(tick, 12, episode) && episode;
   }

   public int score() { return evidence.score(); }
   public int episodes() { return evidence.episodes(); }
   public int regularEpisodes() { return regularEpisodes; }
}
