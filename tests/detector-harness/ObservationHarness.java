import first.rain.anticheat.util.anticheat.checks.ObservationEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ObservationHarness {
   private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
   private static final UUID VICTIM = UUID.fromString("00000000-0000-0000-0000-000000000002");
   private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");
   private static int assertions;

   private static void check(boolean value, String message) {
      assertions++;
      if (!value) throw new AssertionError(message);
   }

   private static ObservationEngine.Sample sample(UUID id, double x, boolean swing, int hurt, int ping) {
      return new ObservationEngine.Sample(id, x, 64.0, 0.0, 1.62, 1.8,
         -90.0f, 0.0f, swing, hurt, ping, false, true);
   }

   private static ObservationEngine.Sample creativeActor(boolean swing) {
      return new ObservationEngine.Sample(ACTOR, 0.0, 64.0, 0.0, 1.62, 1.8,
         -90.0f, 0.0f, swing, 0, 40, false, false);
   }

   private static List<ObservationEngine.Evidence> frame(ObservationEngine engine, int tick,
      double victimX, boolean swing, int hurt, int ping, boolean crowd) {
      List<ObservationEngine.Sample> batch = new ArrayList<ObservationEngine.Sample>();
      batch.add(sample(ACTOR, 0.0, swing, 0, ping));
      batch.add(sample(VICTIM, victimX, false, hurt, ping));
      if (crowd) batch.add(sample(OTHER, victimX + 0.3, false, 0, ping));
      return engine.observe(tick, 1000000000L + tick * 50000000L, batch);
   }

   private static void scenario(double x, int ping, boolean crowd, boolean expected) {
      ObservationEngine engine = new ObservationEngine();
      List<ObservationEngine.Evidence> result = null;
      for (int tick = 1; tick <= 7; tick++)
         result = frame(engine, tick, x, tick == 6, tick == 7 ? 9 : 0, ping, crowd);
      check((result != null && !result.isEmpty()) == expected, "scenario x=" + x
         + " ping=" + ping + " crowd=" + crowd);
      if (expected) {
         ObservationEngine.Evidence evidence = result.get(0);
         check(evidence.minimumDistance > 5.0, "broad legal envelope");
         check(evidence.independentEpisodes == 1, "one episode stays one episode");
         check(evidence.reason.contains("damage cause"), "uncertainty preserved");
         check(evidence.actorHistory.size() == 5 && evidence.victimHistory.size() == 5,
            "bounded replay context");
         check(Math.abs(ObservationEngine.replayMinimumDistance(evidence)
            - evidence.minimumDistance) < 0.000001, "detector measurement replays");
      } else {
         check(!engine.candidates().isEmpty(), "abstention or legal alternative recorded");
      }
   }

   public static void main(String[] args) {
      scenario(3.3, 40, false, false); // subtle reach is not identifiable
      scenario(5.6, 40, false, true);
      scenario(5.6, 220, false, false);
      scenario(5.6, 40, true, false);
      ObservationEngine engine = new ObservationEngine();
      for (int tick = 1; tick <= 7; tick++)
         frame(engine, tick, 5.6, tick == 6, tick == 7 ? 9 : 0, 40, false);
      List<ObservationEngine.Evidence> second = null;
      for (int tick = 8; tick <= 29; tick++)
         second = frame(engine, tick, 5.6, tick == 28, tick == 29 ? 9 : 0, 40, false);
      check(second.size() == 1 && second.get(0).independentEpisodes == 2,
         "separated episodes accumulate");
      check(engine.reviews().size() == 2, "bounded review transcript retains both");
      check(engine.hasContinuousQuality(ACTOR), "continuous quality");
      frame(engine, 31, 5.6, false, 0, 40, false);
      check(!engine.hasContinuousQuality(ACTOR), "missing tick invalidates quality");
      engine.clear();
      check(engine.reviews().isEmpty() && !engine.hasContinuousQuality(ACTOR),
         "world/disconnect cleanup");
      ObservationEngine moving = new ObservationEngine();
      List<ObservationEngine.Evidence> result = null;
      for (int tick = 1; tick <= 7; tick++)
         result = frame(moving, tick, 5.6 + tick * 0.3, tick == 6,
            tick == 7 ? 9 : 0, 40, false);
      check(result.isEmpty(), "moving combat abstains because timing uncertainty dominates");
      ObservationEngine disabled = new ObservationEngine();
      disabled.configure(false, 150, 1.5, 0.5);
      for (int tick = 1; tick <= 7; tick++)
         result = frame(disabled, tick, 5.6, tick == 6, tick == 7 ? 9 : 0, 40, false);
      check(result.isEmpty(), "configured reach review disabled");
      ObservationEngine creative = new ObservationEngine();
      for (int tick = 1; tick <= 7; tick++) {
         List<ObservationEngine.Sample> batch = new ArrayList<ObservationEngine.Sample>();
         batch.add(creativeActor(tick == 6));
         batch.add(sample(VICTIM, 5.6, false, tick == 7 ? 9 : 0, 40));
         result = creative.observe(tick, 1000000000L + tick * 50000000L, batch);
      }
      check(result.isEmpty(), "creative attacker is outside vanilla survival reach profile");
      ObservationEngine attribution = new ObservationEngine();
      for (int tick = 1; tick <= 7; tick++) {
         List<ObservationEngine.Sample> batch = new ArrayList<ObservationEngine.Sample>();
         batch.add(sample(ACTOR, 0, tick == 6, 0, 40));
         batch.add(sample(VICTIM, 2.5, false, tick == 7 ? 9 : 0, 40));
         batch.add(sample(OTHER, 2.8, tick == 6, 0, 40));
         attribution.observe(tick, 1000000000L + tick * 50000000L, batch);
      }
      check(!attribution.unambiguousSwing(ACTOR, VICTIM, 7),
         "second nearby swinger prevents attack attribution");
      ObservationEngine baseline = new ObservationEngine();
      baseline.setTemporalEnabled(true);
      for (int tick = 1; tick <= 40; tick++) {
         ObservationEngine.Sample turning = new ObservationEngine.Sample(ACTOR, 0, 64, 0,
            1.62, 1.8, tick * 2.0f, 0, false, 0, 40, false, true);
         baseline.observe(tick, 1000000000L + tick * 50000000L,
            java.util.Collections.singletonList(turning));
      }
      check(Math.abs(baseline.calmTurnBaseline(ACTOR) - 2.0D) < 0.01D,
         "long bounded history supplies calm turn baseline");
      baseline.setTemporalEnabled(false);
      check(Double.isNaN(baseline.calmTurnBaseline(ACTOR)),
         "disabling temporal mode drops long history");
      ObservationEngine environment = new ObservationEngine();
      for (int tick = 1; tick <= 6; tick++) {
         ObservationEngine.Sample scene = new ObservationEngine.Sample(ACTOR, 0, 64, 0,
            1.62, 1.8, 0, 0, false, 0, 40, false, true,
            false, false, false, true, tick == 3, Double.NaN);
         environment.observe(tick, 1000000000L + tick * 50000000L,
            java.util.Collections.singletonList(scene));
      }
      check(!environment.hasContinuousQuality(ACTOR),
         "recent collision context delays legacy detector judgement");
      System.out.println("OBSERVATION HARNESS OK: " + assertions + " assertions");
   }
}
