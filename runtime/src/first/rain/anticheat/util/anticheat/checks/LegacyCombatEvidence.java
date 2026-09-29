package first.rain.anticheat.util.anticheat.checks;

/** Restored original rotation, movement-fix and consume patterns. The game
 * adapter supplies quality-gated combat observations; results remain review evidence. */
public final class LegacyCombatEvidence {
   private static final float QUANTUM = 1.40625F;
   private final float[] rotations = new float[10];
   private int rotationCount, snapStreak, moveSamples, lockHits, sprintHits, moveTicks;
   private int useTicks, consumeVl;
   private float residualSum, score;
   private double lastX, lastY, lastZ;
   private boolean hasVelocity;
   private long lastEat = Long.MIN_VALUE;
   private String reasons = "";

   public void rotation(float yawChange, float pitchChange) {
      if (yawChange == 0.0F && pitchChange == 0.0F) return;
      rotations[rotationCount++] = Math.abs(yawChange);
      if (rotationCount < rotations.length) return;
      int machine = 0, constant = 0, robot = 0, up = 0, down = 0;
      float previous = rotations[0];
      for (float change : rotations) {
         float difference = Math.abs(change - rotations[0]);
         if (difference < QUANTUM * 1.5F && change > QUANTUM * 2.0F) robot++;
         if (difference < QUANTUM && change > QUANTUM * 3.0F) machine++;
         if (difference < QUANTUM * 0.5F && change > QUANTUM * 2.5F) constant++;
         if (change - previous > 12.0F) up++;
         if (change - previous < -12.0F) down++;
         previous = change;
      }
      if (machine > 8) add(100, "heuristic(aim)");
      if (constant > 6) add(65, "heuristic(constant)");
      if (robot > 8) add(50, "heuristic(sync)");
      if (up > 1 && down > 1 && up + down > 4) {
         if (++snapStreak > 2) add(55, "pattern(snap)");
      } else snapStreak = 0;
      rotationCount = 0;
   }

   public void movement(double x, double y, double z, float yaw,
      boolean usableGround, boolean sprinting, boolean targetLocked) {
      if (++moveTicks >= 40) {
         moveTicks = 0;
         lockHits = Math.max(0, lockHits - 1);
         sprintHits = Math.max(0, sprintHits - 1);
      }
      boolean flat = hasVelocity && Math.abs(y) < 0.001D && Math.abs(lastY) < 0.001D;
      double accel = Math.hypot(x - lastX, z - lastZ);
      lastX = x; lastY = y; lastZ = z; hasVelocity = true;
      double speed = Math.hypot(x, z);
      if (!usableGround || !flat || speed < 0.15D || speed > 0.45D) {
         moveSamples = 0; residualSum = 0; lockHits = sprintHits = 0;
         return;
      }
      float offset = wrap((float)Math.toDegrees(Math.atan2(-x, z)) - yaw);
      float residual = Math.abs(wrap(offset - 45.0F * Math.round(offset / 45.0F)));
      if (sprinting && speed > 0.25D && accel < 0.08D && Math.abs(offset) > 62.0F) {
         if (++sprintHits >= 4) { add(85, "movement(sprint)"); sprintHits -= 4; }
      }
      if (accel > 0.022D) return;
      if (residual > 13.0F && targetLocked) {
         if (++lockHits >= 3) { add(85, "movement(lock)"); lockHits -= 3; }
      }
      residualSum += residual;
      if (++moveSamples >= 12) {
         if (residualSum / moveSamples > 7.5F) add(70, "movement(fix)");
         moveSamples = 0; residualSum = 0;
      }
   }

   /** Original consume timing, with an explicit sentinel for the last use. */
   public boolean consume(long tick, boolean usingConsumable, boolean attacking) {
      if (usingConsumable) useTicks++;
      else {
         if (useTicks > 0) lastEat = tick;
         useTicks = 0;
      }
      if (attacking && useTicks > 6 && lastEat != Long.MIN_VALUE
         && tick >= lastEat && tick - lastEat < 33) consumeVl++;
      else consumeVl = Math.max(0, consumeVl - 1);
      return consumeVl >= 8;
   }

   private void add(float amount, String reason) {
      score = Math.min(800.0F, score + amount);
      if (!reasons.contains(reason)) reasons += (reasons.isEmpty() ? "" : ", ") + reason;
   }
   public boolean shouldReview() { return score > 400.0F; }
   public float score() { return score; }
   public String reasons() { return reasons; }
   public void afterReview() { score = 360.0F; reasons = ""; }
   public void tick() {
      score = Math.max(0, score - 0.5F);
      if (score == 0) reasons = "";
   }
   /** Discard incomplete windows when combat context stops being usable. */
   public void breakObservation() {
      rotationCount = snapStreak = moveSamples = lockHits = sprintHits = useTicks = consumeVl = 0;
      residualSum = 0; hasVelocity = false; lastEat = Long.MIN_VALUE;
   }
   public void reset() { breakObservation(); score = 0; reasons = ""; moveTicks = 0; }
   private static float wrap(float angle) {
      angle %= 360.0F;
      if (angle >= 180.0F) angle -= 360.0F;
      if (angle < -180.0F) angle += 360.0F;
      return angle;
   }
}
