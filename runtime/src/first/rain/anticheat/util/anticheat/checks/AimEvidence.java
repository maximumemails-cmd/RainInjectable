package first.rain.anticheat.util.anticheat.checks;

/** Score policy for remote aim observations. Return bursts share the snap
 * source; they cannot independently satisfy the multi-signal requirement. */
public final class AimEvidence {
   public static final int SNAP = 1;
   public static final int RETURN = 2;
   public static final int TRACK = 3;
   private float score;
   private int sourceMask;
   private int combatHits;

   public void tick(boolean inCombat) {
      score = Math.max(0.0F, score - (inCombat ? 0.8F : 1.5F));
      if (score == 0.0F) sourceMask = 0;
   }

   public void combatHit() { combatHits = Math.min(100, combatHits + 1); }

   public void add(int source, float weight) {
      score = Math.min(300.0F, score + weight);
      if (source == SNAP || source == RETURN) sourceMask |= 1;
      if (source == TRACK) sourceMask |= 2;
   }

   public boolean shouldAlert() {
      return score >= 220.0F && sourceMask == 3 && combatHits >= 3;
   }

   public void afterAlert() { score = 100.0F; sourceMask = 0; combatHits = 0; }
   public void reset() { score = 0.0F; sourceMask = 0; combatHits = 0; }
   public float score() { return score; }
   public int combatHits() { return combatHits; }
   public int sourceMask() { return sourceMask; }
}
