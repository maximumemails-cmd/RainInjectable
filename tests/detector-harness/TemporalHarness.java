import first.rain.anticheat.util.anticheat.checks.ObservationEngine;
import first.rain.anticheat.util.anticheat.checks.TemporalAnalysis;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TemporalHarness {
   private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
   private static int assertions;
   private static void check(boolean condition, String message) {
      assertions++;
      if (!condition) throw new AssertionError(message);
   }
   private static ObservationEngine.Sample sample(double x, boolean sneak, int ping,
      boolean block, boolean uncertain, double edge) {
      return new ObservationEngine.Sample(ID, x, 64, 0, 1.62, 1.8,
         0, 75, false, 0, ping, false, true, sneak, false,
         block, true, uncertain, edge);
   }
   private static int run(double spread, int ping, boolean block, boolean uncertain,
      boolean everyEdge) {
      TemporalAnalysis engine = new TemporalAnalysis();
      int alerts = 0;
      for (int tick = 1; tick <= 64; tick++) {
         int segment = (tick - 1) / 8;
         int phase = (tick - 1) % 8;
         boolean onset = phase == 5;
         double x = segment + (onset ? 0.82 + (segment % 2 == 0 ? spread : -spread)
            : phase < 5 ? 0.12 + phase * 0.12 : 0.85);
         boolean sneak = phase >= 5 && phase <= 6;
         double edge = onset && everyEdge ? 1.0 - (x - Math.floor(x)) : Double.NaN;
         List<TemporalAnalysis.Signal> signals = engine.observe(tick,
            Collections.singletonList(sample(x, sneak, ping, block, uncertain, edge)));
         alerts += signals.size();
         if (!signals.isEmpty()) {
            check(signals.get(0).episodes >= 7, "repeated distinct edges required");
            check(signals.get(0).reliability < 1.0, "manual bridging alternative retained");
         }
      }
      check(engine.trackedPlayers() == 1, "bounded one-player track");
      engine.clear();
      check(engine.trackedPlayers() == 0, "cleanup releases track");
      return alerts;
   }
   public static void main(String[] args) {
      check(run(0.0, 40, true, false, true) == 1, "mechanically repeated edge crouches review once");
      check(run(0.09, 40, true, false, true) == 0, "variable skilled bridge does not review");
      check(run(0.0, 230, true, false, true) == 0, "high ping abstains");
      check(run(0.0, 40, false, false, true) == 0, "ordinary edge crouch without blocks abstains");
      check(run(0.0, 40, true, true, true) == 0, "terrain or collision uncertainty abstains");
      check(run(0.0, 40, true, false, false) == 0, "sneak timing without a measured edge abstains");
      TemporalAnalysis lagged = new TemporalAnalysis();
      lagged.observe(1, 1000000000L, Collections.singletonList(sample(0, false, 40, true, false, 0.2)));
      lagged.observe(2, 1300000000L, Collections.singletonList(sample(0.2, true, 40, true, false, 0.2)));
      check(lagged.trackedPlayers() == 1, "global observer gap resets and restarts history");
      System.out.println("TEMPORAL HARNESS OK: " + assertions + " assertions");
   }
}
