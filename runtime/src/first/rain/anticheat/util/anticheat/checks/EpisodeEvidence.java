package first.rain.anticheat.util.anticheat.checks;

/** Small, game-independent evidence pool. One event contributes at most once;
 * gaps and ordinary observations drain it. Ticks are world ticks, not wall time. */
public final class EpisodeEvidence {
   private final int threshold;
   private final int minimumEpisodes;
   private final int maxGap;
   private int score;
   private int episodes;
   private long lastTick = Long.MIN_VALUE;
   private long lastEpisode = Long.MIN_VALUE;

   public EpisodeEvidence(int threshold, int minimumEpisodes, int maxGap) {
      this.threshold = threshold;
      this.minimumEpisodes = minimumEpisodes;
      this.maxGap = maxGap;
   }

   public boolean observe(long tick, int evidence, boolean episode) {
      if (lastTick != Long.MIN_VALUE) {
         long gap = tick - lastTick;
         if (gap <= 0 || gap > maxGap) {
            reset();
         } else {
            score = Math.max(0, score - (int)Math.min(gap, 4));
         }
      }
      lastTick = tick;
      if (lastEpisode != Long.MIN_VALUE && tick - lastEpisode > 80) {
         episodes = 0;
      }
      if (episode && tick != lastEpisode) {
         lastEpisode = tick;
         episodes = Math.min(32, episodes + 1);
         score = Math.min(threshold + 20, score + Math.max(0, evidence));
      }
      if (score == 0) {
         episodes = 0;
      }
      return score >= threshold && episodes >= minimumEpisodes;
   }

   public int score() { return score; }
   public int episodes() { return episodes; }
   public void reset() {
      score = 0;
      episodes = 0;
      lastTick = Long.MIN_VALUE;
      lastEpisode = Long.MIN_VALUE;
   }
}
