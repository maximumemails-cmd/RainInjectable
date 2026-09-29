import first.rain.anticheat.util.anticheat.checks.LegacyCombatEvidence;
import first.rain.anticheat.util.anticheat.FlashPulse;

public final class RestorationHarness {
   private static int assertions;
   private static void check(boolean pass, String message) {
      assertions++;
      if (!pass) throw new AssertionError(message);
   }
   private static LegacyCombatEvidence movement(float yaw, boolean usable, boolean sprint, boolean locked) {
      LegacyCombatEvidence e = new LegacyCombatEvidence();
      for (int i = 0; i < 120; i++) {
         e.movement(0, 0, 0.3D, yaw, usable, sprint, locked);
         e.tick();
      }
      return e;
   }
   public static void main(String[] args) {
      LegacyCombatEvidence e = new LegacyCombatEvidence();
      for (int i = 0; i < 100; i++) { e.rotation(0, 0); e.tick(); }
      check(e.score() == 0, "stationary aim stays quiet");
      for (int i = 0; i < 20; i++) { e.rotation(8, 0); e.tick(); }
      check(e.shouldReview(), "original constant rotation windows restored");
      check(e.reasons().contains("heuristic(aim)") && e.reasons().contains("heuristic(constant)")
         && e.reasons().contains("heuristic(sync)"), "all three rotation heuristics restored");
      e.reset();
      for (int i = 0; i < 100; i++) e.rotation(i % 2 == 0 ? 2 : 30, 0);
      check(e.shouldReview() && e.reasons().contains("pattern(snap)"), "persistent oscillation restored");
      e.reset();
      for (int i = 0; i < 100; i++) { e.rotation(i % 9 * 1.3F, 0.2F); e.tick(); }
      check(!e.shouldReview(), "varied ordinary turns stay quiet");
      check(!movement(0, true, true, false).shouldReview(), "forward sprint stays quiet");
      check(!movement(45, true, false, false).shouldReview(), "legal diagonal stays quiet");
      check(!movement(20, false, true, true).shouldReview(), "uncertain terrain suppresses movement");
      check(movement(12, true, false, false).reasons().contains("movement(fix)"), "movement-fix residual restored");
      check(movement(20, true, false, true).reasons().contains("movement(lock)"), "target-locked movement restored");
      check(movement(90, true, true, false).reasons().contains("movement(sprint)"), "sprint direction restored");
      e.reset();
      for (int i = 0; i < 25; i++) check(!e.consume(i, true, false), "ordinary eating stays quiet");
      e.consume(25, false, false);
      boolean consume = false;
      for (int i = 26; i < 45; i++) consume |= e.consume(i, true, true);
      check(consume, "repeat attack/consume overlap restored");
      e.reset();
      check(!e.consume(46, true, true) && !e.shouldReview(), "reset clears evidence and consume timing");
      for (int i = 0; i < 9; i++) e.rotation(8, 0);
      e.breakObservation(); e.rotation(8, 0);
      check(e.score() == 0, "gaps discard partial rotation windows");

      FlashPulse flash = new FlashPulse();
      check(flash.color(100, true, 65, 0x123456) == 0, "no flash before trigger");
      flash.start(100, false);
      int start = flash.color(100, true, 65, 0x123456);
      check((start >>> 24) == 165 && (start & 0xFFFFFF) == 0x123456, "configured color and opacity");
      check((flash.color(400, true, 65, 0) >>> 24) < (flash.color(700, true, 65, 0) >>> 24), "second pulse rises");
      check(flash.color(1300, true, 65, 0) == 0, "flash expires");
      flash.start(2000, false);
      check(flash.color(2001, false, 65, 0) == 0, "disabling cancels alerts");
      flash.start(3000, true);
      check((flash.color(3001, false, 65, 0) >>> 24) > 0, "test works with master and flash disabled");
      check((flash.color(3001, false, 0, 0) >>> 24) == 0, "zero opacity respected");
      flash.start(4000, true);
      check((flash.color(4000, false, 200, 0) >>> 24) == 255, "opacity clamped");
      flash.clear();
      check(flash.color(4001, true, 65, 0) == 0, "world reset clears flash");
      System.out.println("RESTORATION HARNESS OK: " + assertions + " assertions");
   }
}
