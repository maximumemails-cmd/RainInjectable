package first.rain.anticheat.util.anticheat.checks;

/** Edge and timing state for block/swing overlap. Hurt-correlated combat may
 * arrive a few ticks after the animation event. */
public final class AutoBlockEvidence {
   private final EpisodeEvidence evidence = new EpisodeEvidence(16, 4, 6);
   private boolean wasSwinging;
   private boolean wasBlocking;
   private long lastEpisode = Long.MIN_VALUE;
   private long pendingOverlap = Long.MIN_VALUE;
   private long lastTick = Long.MIN_VALUE;
   private int continuousBlockTicks;
   private int overlapsInBlockRun;

   public boolean observe(long tick, boolean swing, boolean block, boolean combat) {
      if (lastTick != Long.MIN_VALUE && (tick <= lastTick || tick - lastTick > 2)) {
         evidence.reset();
         wasSwinging = false;
         wasBlocking = false;
         lastEpisode = Long.MIN_VALUE;
         pendingOverlap = Long.MIN_VALUE;
         continuousBlockTicks = 0;
         overlapsInBlockRun = 0;
      }
      lastTick = tick;
      if (block) {
         continuousBlockTicks = Math.min(100, continuousBlockTicks + 1);
      } else {
         continuousBlockTicks = 0;
         overlapsInBlockRun = 0;
         pendingOverlap = Long.MIN_VALUE;
      }
      if (block && swing && (!wasSwinging || !wasBlocking)) pendingOverlap = tick;
      boolean correlated = combat && block && pendingOverlap != Long.MIN_VALUE
         && tick - pendingOverlap >= 0 && tick - pendingOverlap <= 4;
      if (correlated) {
         overlapsInBlockRun++;
         pendingOverlap = Long.MIN_VALUE;
      }
      boolean episode = correlated && continuousBlockTicks >= 3 && overlapsInBlockRun >= 2
         && (lastEpisode == Long.MIN_VALUE || tick - lastEpisode >= 3);
      if (episode) lastEpisode = tick;
      if (pendingOverlap != Long.MIN_VALUE && tick - pendingOverlap > 4) pendingOverlap = Long.MIN_VALUE;
      wasSwinging = swing;
      wasBlocking = block;
      return evidence.observe(tick, 7, episode) && episode;
   }

   public int score() { return evidence.score(); }
   public int episodes() { return evidence.episodes(); }
}
