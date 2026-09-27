import first.rain.anticheat.util.anticheat.checks.AimEvidence;
import first.rain.anticheat.util.anticheat.checks.AimGeometry;
import first.rain.anticheat.util.anticheat.checks.AutoBlockEvidence;
import first.rain.anticheat.util.anticheat.checks.EpisodeEvidence;
import first.rain.anticheat.util.anticheat.checks.ScaffoldEvidence;

public final class Harness {
   private static int checks;
   private static void expect(boolean condition, String message) {
      checks++;
      if (!condition) throw new AssertionError(message);
   }

   public static void main(String[] args) {
      autoBlock();
      scaffold();
      detectorTraces();
      aim();
      geometry();
      noisyAndLifecycle();
      System.out.println("DETECTOR HARNESS OK: " + checks + " assertions");
   }

   private static void autoBlock() {
      EpisodeEvidence e = new EpisodeEvidence(16, 4, 6);
      // Ordinary swings/block use and one odd overlap cannot alert.
      for (int t = 0; t < 20; t++) expect(!e.observe(t, 7, t == 3), "normal AutoBlock");
      expect(e.score() == 0, "AutoBlock recovery");
      for (int t = 20; t <= 36; t++) {
         boolean hit = t == 20 || t == 24 || t == 28 || t == 32 || t == 36;
         boolean alert = e.observe(t, 7, hit);
         if (t < 32) expect(!alert, "AutoBlock early flag");
      }
      expect(e.score() >= 16 && e.episodes() >= 4, "AutoBlock repeated episodes");
      for (int t = 37; t < 70; t++) e.observe(t, 0, false);
      expect(e.score() == 0, "AutoBlock decay");
   }

   private static void scaffold() {
      EpisodeEvidence e = new EpisodeEvidence(26, 5, 20);
      for (int t = 0; t <= 40; t++) {
         boolean suspicious = t == 5 || t == 10 || t == 15;
         expect(!e.observe(t, 12, suspicious), "normal bridging / isolated crouches");
      }
      e.reset();
      for (int t = 0; t <= 35; t++) {
         boolean repeated = t > 0 && t % 5 == 0;
         boolean alert = e.observe(t, 12, repeated);
         if (t < 25) expect(!alert, "Scaffold early flag");
      }
      expect(e.score() >= 26 && e.episodes() >= 5, "Scaffold repeated rhythm");
   }

   private static void aim() {
      AimEvidence e = new AimEvidence();
      for (int i = 0; i < 100; i++) {
         e.tick(true); // skilled smooth tracking alone
         if (i % 20 == 0) e.add(AimEvidence.TRACK, 80);
      }
      expect(!e.shouldAlert(), "accurate aim alone");
      e.reset();
      for (int i = 0; i < 3; i++) e.combatHit();
      e.add(AimEvidence.SNAP, 90);
      e.add(AimEvidence.RETURN, 55);
      e.add(AimEvidence.SNAP, 90);
      expect(!e.shouldAlert(), "snap and return are correlated");
      e.add(AimEvidence.TRACK, 80);
      expect(e.shouldAlert(), "independent repeated aim evidence");
      e.afterAlert();
      expect(!e.shouldAlert(), "alert cooldown score drop");
      expect(e.sourceMask() == 0 && e.combatHits() == 0, "fresh evidence required after alert");
      for (int i = 0; i < 200; i++) e.tick(false);
      expect(e.score() == 0 && e.sourceMask() == 0, "aim recovery");
      e.reset();
      e.add(AimEvidence.SNAP, 90);
      e.add(AimEvidence.TRACK, 80);
      expect(!e.shouldAlert(), "missing combat observations");
   }

   private static void noisyAndLifecycle() {
      EpisodeEvidence e = new EpisodeEvidence(16, 4, 6);
      e.observe(1, 7, true);
      e.observe(2, 0, false);
      e.observe(4, 7, true); // skipped tick
      expect(!e.observe(12, 7, true), "long observation gap resets evidence");
      expect(e.episodes() == 1, "gap count reset");
      e.reset();
      expect(e.score() == 0 && e.episodes() == 0, "player removal / world unload reset");
      AimEvidence aim = new AimEvidence();
      aim.combatHit();
      aim.add(AimEvidence.SNAP, 90);
      aim.reset();
      expect(aim.score() == 0 && aim.combatHits() == 0, "master off / rejoin reset");
   }

   private static void geometry() {
      // Facing a body at eye level versus looking above it.
      float inside = AimGeometry.outsideBoxDegrees(0, 1.62, 0, -90, 0,
         3, 0, 0, 0.4, 1.8);
      float high = AimGeometry.outsideBoxDegrees(0, 1.62, 0, -90, -65,
         3, 0, 0, 0.4, 1.8);
      float aside = AimGeometry.outsideBoxDegrees(0, 1.62, 0, 0, 0,
         3, 0, 0, 0.4, 1.8);
      expect(inside == 0.0F, "target body in aim cone");
      expect(high > 20.0F, "pitch matters");
      expect(aside > 60.0F, "yaw matters");
      expect(AimGeometry.outsideBoxDegrees(0, 1.62, 0, -90, 0,
         0.1, 0, 0, 0.4, 1.8) == Float.MAX_VALUE, "overlap is ambiguous");
   }

   private static void detectorTraces() {
      AutoBlockEvidence ordinary = new AutoBlockEvidence();
      for (int t = 0; t < 70; t++) {
         boolean odd = t == 7 || t == 35;
         expect(!ordinary.observe(t, odd, odd, false), "block use without combat");
      }
      AutoBlockEvidence abnormal = new AutoBlockEvidence();
      boolean autoAlert = false;
      for (int t = 0; t < 60; t++) {
         boolean overlap = t >= 10 && t <= 30 && (t - 10) % 4 == 0;
         boolean delayedHurt = t >= 11 && t <= 31 && (t - 11) % 4 == 0;
         autoAlert |= abnormal.observe(t, overlap, t >= 10 && t <= 31, delayedHurt);
         if (t < 23) expect(!autoAlert, "AutoBlock waits for episodes");
      }
      expect(autoAlert, "delayed combat still correlates AutoBlock episodes");
      AutoBlockEvidence manual = new AutoBlockEvidence();
      for (int t = 0; t < 60; t++) {
         boolean overlap = t >= 10 && t <= 30 && (t - 10) % 4 == 0;
         boolean hurt = t >= 11 && t <= 31 && (t - 11) % 4 == 0;
         expect(!manual.observe(t, overlap, overlap || hurt, hurt), "manual block release between hits");
      }
      AutoBlockEvidence frozen = new AutoBlockEvidence();
      frozen.observe(1, true, true, false);
      expect(!frozen.observe(9, false, false, true), "stale overlap after freeze");

      ScaffoldEvidence bridge = new ScaffoldEvidence();
      for (int t = 0; t < 70; t++) {
         // A skilled bridge may use quick crouches, but varied durations and
         // block-use swings do not form a repeated mechanical rhythm.
         boolean sneak = (t % 9 < (t / 9) % 3 + 1);
         boolean swing = t % 9 == 3;
         expect(!bridge.observe(t, sneak, swing, true), "normal varied bridging");
      }
      ScaffoldEvidence repeated = new ScaffoldEvidence();
      boolean scaffoldAlert = false;
      for (int t = 0; t < 65; t++) {
         boolean sneak = t >= 5 && t <= 45 && t % 5 == 0;
         boolean swing = t >= 7 && t <= 47 && t % 5 == 2;
         scaffoldAlert |= repeated.observe(t, sneak, swing, true);
      }
      expect(scaffoldAlert, "repeated short crouch / delayed swing rhythm");
      ScaffoldEvidence interrupted = new ScaffoldEvidence();
      interrupted.observe(1, true, false, true);
      expect(!interrupted.observe(10, false, true, true), "stale crouch after freeze");
      for (int t = 65; t < 115; t++) repeated.observe(t, false, false, false);
      expect(repeated.score() == 0, "scaffold recovery");
   }
}
