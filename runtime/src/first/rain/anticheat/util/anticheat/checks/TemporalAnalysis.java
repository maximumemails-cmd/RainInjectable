package first.rain.anticheat.util.anticheat.checks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short tick-presentation history. No remote key presses or placement packets are inferred. */
public final class TemporalAnalysis {
   public static final int WINDOW_TICKS = 80;
   private static final int EVENTS = 12;
   private final Map<UUID, Track> tracks = new HashMap<UUID, Track>();
   private final Set<UUID> seen = new HashSet<UUID>();
   private long lastGlobalTick = Long.MIN_VALUE, lastNanos = Long.MIN_VALUE;

   public static final class Signal {
      public final UUID player;
      public final String name, explanation;
      public final int episodes;
      public final double strength, reliability;
      Signal(UUID player, String name, String explanation, int episodes,
         double strength, double reliability) {
         this.player = player; this.name = name; this.explanation = explanation;
         this.episodes = episodes; this.strength = strength; this.reliability = reliability;
      }
   }

   private static final class Track {
      final ObservationEngine.Sample[] samples = new ObservationEngine.Sample[WINDOW_TICKS];
      final double[] edge = new double[EVENTS];
      final long[] onsetTick = new long[EVENTS];
      final long[] blockCell = new long[EVENTS];
      int head, size, eventHead, eventSize;
      long lastTick = Long.MIN_VALUE, lastAlert = Long.MIN_VALUE;
      void clearPattern() { eventHead = 0; eventSize = 0; }
      void push(ObservationEngine.Sample sample) {
         samples[head] = sample;
         head = (head + 1) % WINDOW_TICKS;
         size = Math.min(WINDOW_TICKS, size + 1);
      }
      ObservationEngine.Sample previous() {
         return size == 0 ? null : samples[(head - 1 + WINDOW_TICKS) % WINDOW_TICKS];
      }
      void event(double d, long tick, long cell) {
         edge[eventHead] = d; onsetTick[eventHead] = tick; blockCell[eventHead] = cell;
         eventHead = (eventHead + 1) % EVENTS;
         eventSize = Math.min(EVENTS, eventSize + 1);
      }
   }

   public List<Signal> observe(long tick, List<ObservationEngine.Sample> batch) {
      return observe(tick, Long.MIN_VALUE, batch);
   }

   public List<Signal> observe(long tick, long nanos, List<ObservationEngine.Sample> batch) {
      if (lastGlobalTick != Long.MIN_VALUE && (tick != lastGlobalTick + 1
         || (nanos != Long.MIN_VALUE && lastNanos != Long.MIN_VALUE
            && (nanos <= lastNanos || nanos - lastNanos > 150000000L)))) tracks.clear();
      lastGlobalTick = tick;
      lastNanos = nanos;
      List<Signal> result = new ArrayList<Signal>(1);
      seen.clear();
      for (ObservationEngine.Sample sample : batch) {
         if (sample == null || sample.id == null) continue;
         seen.add(sample.id);
         Track track = tracks.get(sample.id);
         if (track == null) { track = new Track(); tracks.put(sample.id, track); }
         ObservationEngine.Sample prior = track.previous();
         if (track.lastTick != Long.MIN_VALUE && (tick != track.lastTick + 1
            || prior == null || distance(prior, sample) > 1.5D
            || Math.abs(prior.ping - sample.ping) > 80)) {
            track.clearPattern(); track.size = 0; prior = null;
         }
         track.lastTick = tick;
         track.push(sample);
         boolean usable = sample.survival && sample.grounded && sample.blockHeld
            && !sample.riding && !sample.uncertainEnvironment && sample.ping >= 0
            && sample.ping <= 150 && Double.isFinite(sample.edgeDistance);
         if (usable && prior != null && !prior.sneak && sample.sneak
            && distance(prior, sample) >= 0.06D && sample.edgeDistance <= 0.32D) {
            long cell = (((long)Math.floor(sample.x)) << 32)
               ^ (((long)Math.floor(sample.z)) & 0xffffffffL);
            if (track.eventSize == 0 || track.blockCell[(track.eventHead - 1 + EVENTS) % EVENTS] != cell) {
               track.event(sample.edgeDistance, tick, cell);
               Signal signal = assess(sample.id, track, tick);
               if (signal != null) result.add(signal);
            }
         }
         if (!usable && (sample.uncertainEnvironment || sample.ping < 0
            || sample.ping > 150 || sample.riding)) track.clearPattern();
      }
      tracks.keySet().retainAll(seen);
      return result;
   }

   private static Signal assess(UUID id, Track track, long tick) {
      int n = 0;
      double mean = 0.0D, square = 0.0D;
      long first = Long.MIN_VALUE, last = Long.MIN_VALUE;
      int distinct = 0;
      long priorCell = Long.MIN_VALUE;
      for (int i = 0; i < track.eventSize; i++) {
         int k = (track.eventHead - track.eventSize + i + EVENTS) % EVENTS;
         if (tick - track.onsetTick[k] >= WINDOW_TICKS) continue;
         double d = track.edge[k];
         mean += d; square += d * d; n++;
         if (first == Long.MIN_VALUE) first = track.onsetTick[k];
         last = track.onsetTick[k];
         if (track.blockCell[k] != priorCell) { distinct++; priorCell = track.blockCell[k]; }
      }
      if (n < 7 || distinct < 7 || last - first < 25 ||
         (track.lastAlert != Long.MIN_VALUE && tick - track.lastAlert < 80)) return null;
      mean /= n;
      double sigma = Math.sqrt(Math.max(0.0D, square / n - mean * mean));
      if (sigma > 0.035D) return null;
      track.lastAlert = tick;
      double strength = Math.min(1.0D, (0.035D - sigma) / 0.035D * 0.65D
         + (n - 7) * 0.09D);
      return new Signal(id, "edge-crouch pattern",
         "repeated sneak onsets at distinct supported edges; onsets=" + n
            + ", edge spread=" + String.format(java.util.Locale.ROOT, "%.3f", sigma)
            + "; skilled manual bridging remains possible",
         n, strength, 0.65D);
   }

   private static double distance(ObservationEngine.Sample a, ObservationEngine.Sample b) {
      double dx = a.x - b.x, dz = a.z - b.z;
      return Math.sqrt(dx * dx + dz * dz);
   }

   public void retain(Set<UUID> ids) { tracks.keySet().retainAll(ids); }
   public void clear() { tracks.clear(); seen.clear(); lastGlobalTick = Long.MIN_VALUE; lastNanos = Long.MIN_VALUE; }
   public int trackedPlayers() { return tracks.size(); }
}
