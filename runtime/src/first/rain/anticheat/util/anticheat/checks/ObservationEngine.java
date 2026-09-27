package first.rain.anticheat.util.anticheat.checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Passive, tick-snapshot evidence. All timing and attacks here are inferred;
 * this class never treats a relayed animation as a serverbound attack packet.
 * It deliberately emits review evidence, not a calibrated cheat probability.
 */
public final class ObservationEngine {
   public static final int HISTORY = 8;
   public static final double LEGAL_REACH = 3.0D;
   public static final double OBSERVER_SLACK = 1.5D;
   public static final double GROSS_RESIDUAL = 0.5D;
   private static final int MAX_REVIEWS = 64;
   private static final long MAX_TICK_NANOS = 150000000L;
   private final Map<UUID, Track> tracks = new HashMap<UUID, Track>();
   private final List<Evidence> reviews = new ArrayList<Evidence>();
   private final List<Candidate> candidates = new ArrayList<Candidate>();
   private long lastTick = Long.MIN_VALUE;
   private long lastNanos;
   private int maximumPing = 150;
   private double observerSlack = OBSERVER_SLACK;
   private double grossResidual = GROSS_RESIDUAL;
   private boolean reachEnabled = true;

   public void configure(boolean reachEnabled, int maximumPing, double observerSlack,
      double grossResidual) {
      this.reachEnabled = reachEnabled;
      this.maximumPing = Math.max(0, Math.min(1000, maximumPing));
      this.observerSlack = Double.isFinite(observerSlack)
         ? Math.max(OBSERVER_SLACK, Math.min(10.0D, observerSlack)) : OBSERVER_SLACK;
      this.grossResidual = Double.isFinite(grossResidual)
         ? Math.max(GROSS_RESIDUAL, Math.min(10.0D, grossResidual)) : GROSS_RESIDUAL;
   }

   public static final class Sample {
      public final UUID id;
      public final double x, y, z, eyeHeight, height;
      public final float yaw, pitch;
      public final boolean swing, riding, survival;
      public final int hurt, ping;
      public Sample(UUID id, double x, double y, double z, double eyeHeight,
         double height, float yaw, float pitch, boolean swing, int hurt,
         int ping, boolean riding, boolean survival) {
         this.id = id;
         this.x = x; this.y = y; this.z = z;
         this.eyeHeight = eyeHeight; this.height = height;
         this.yaw = yaw; this.pitch = pitch;
         this.swing = swing; this.hurt = hurt; this.ping = ping;
         this.riding = riding;
         this.survival = survival;
      }
   }

   public static final class Evidence {
      public final UUID actor, victim;
      public final long tick;
      public final String hypothesis, reason;
      public final double minimumDistance, residual;
      public final int actorPing, victimPing;
      public final int independentEpisodes;
      public final long monotonicNanos;
      public final List<Sample> actorHistory, victimHistory;
      private Evidence(UUID actor, UUID victim, long tick, String hypothesis,
         String reason, double minimumDistance, double residual,
         int actorPing, int victimPing, int independentEpisodes, long monotonicNanos,
         List<Sample> actorHistory, List<Sample> victimHistory) {
         this.actor = actor; this.victim = victim; this.tick = tick;
         this.hypothesis = hypothesis; this.reason = reason;
         this.minimumDistance = minimumDistance; this.residual = residual;
         this.actorPing = actorPing; this.victimPing = victimPing;
         this.independentEpisodes = independentEpisodes;
         this.monotonicNanos = monotonicNanos;
         this.actorHistory = Collections.unmodifiableList(actorHistory);
         this.victimHistory = Collections.unmodifiableList(victimHistory);
      }
   }

   public static final class Candidate {
      public final UUID victim;
      public final long tick;
      public final List<UUID> nearbyActors;
      public final String outcome, reason;
      public final double minimumDistance;
      private Candidate(UUID victim, long tick, List<UUID> nearbyActors,
         String outcome, String reason, double minimumDistance) {
         this.victim = victim; this.tick = tick;
         this.nearbyActors = Collections.unmodifiableList(new ArrayList<UUID>(nearbyActors));
         this.outcome = outcome; this.reason = reason;
         this.minimumDistance = minimumDistance;
      }
   }

   private static final class Track {
      final Sample[] samples = new Sample[HISTORY];
      int size, head, lastHurt;
      boolean wasSwinging;
      long lastSwingTick = Long.MIN_VALUE;
      long lastObservedTick = Long.MIN_VALUE;
      long lastReviewTick = Long.MIN_VALUE;
      int reachEpisodes;
      void push(Sample sample, long tick) {
         if (lastObservedTick != Long.MIN_VALUE && tick - lastObservedTick != 1) {
            size = 0;
            lastSwingTick = Long.MIN_VALUE;
            reachEpisodes = 0;
         }
         if (size > 0 && distance(sample, at(0)) > 4.0D) {
            size = 0; // teleport/correction: old geometry is no longer a legal bound
            lastSwingTick = Long.MIN_VALUE;
            reachEpisodes = 0;
         }
         samples[head] = sample;
         head = (head + 1) % HISTORY;
         size = Math.min(HISTORY, size + 1);
         if (sample.swing && !wasSwinging) lastSwingTick = tick;
         wasSwinging = sample.swing;
         lastObservedTick = tick;
      }
      Sample at(int age) { return samples[(head - 1 - age + HISTORY) % HISTORY]; }
      boolean stable() {
         if (size < 5) return false;
         Sample current = at(0);
         for (int i = 1; i < 5; i++) {
            Sample old = at(i);
            if (distance(current, old) > 0.35D || old.riding) return false;
         }
         return !current.riding;
      }
   }

   /** One complete world-tick batch, including the local observer as an alternate actor. */
   public List<Evidence> observe(long tick, long nanos, List<Sample> samples) {
      if (lastTick != Long.MIN_VALUE && (tick != lastTick + 1 || nanos <= lastNanos
         || nanos - lastNanos > MAX_TICK_NANOS)) tracks.clear();
      lastTick = tick;
      lastNanos = nanos;
      Set<UUID> seen = new HashSet<UUID>();
      List<UUID> hurtOnsets = new ArrayList<UUID>();
      for (Sample sample : samples) {
         if (sample == null || sample.id == null || !finite(sample)) continue;
         seen.add(sample.id);
         Track track = tracks.get(sample.id);
         if (track == null) { track = new Track(); tracks.put(sample.id, track); }
         boolean freshHurt = track.lastObservedTick == tick - 1
            && sample.hurt >= 7 && sample.hurt > track.lastHurt;
         track.push(sample, tick);
         track.lastHurt = sample.hurt;
         if (freshHurt) hurtOnsets.add(sample.id);
      }
      tracks.keySet().retainAll(seen);
      List<Evidence> emitted = new ArrayList<Evidence>();
      if (!reachEnabled) return emitted;
      for (UUID victimId : hurtOnsets) {
         Track victim = tracks.get(victimId);
         if (victim == null || !victim.stable() || !victim.at(0).survival
            || victim.at(0).ping < 0
            || victim.at(0).ping > maximumPing) {
            candidate(victimId, tick, Collections.<UUID>emptyList(),
               "insufficient", "victim mode, position or ping quality", Double.NaN);
            continue;
         }
         Track actor = null;
         UUID actorId = null;
         int nearby = 0;
         List<UUID> alternatives = new ArrayList<UUID>();
         for (Map.Entry<UUID, Track> entry : tracks.entrySet()) {
            if (entry.getKey().equals(victimId)) continue;
            Track candidate = entry.getValue();
            if (candidate.size == 0 || candidate.lastObservedTick != tick) continue;
            if (distance(candidate.at(0), victim.at(0)) > 6.0D) continue;
            nearby++;
            alternatives.add(entry.getKey());
            if (candidate.lastSwingTick >= tick - 4 && candidate.lastSwingTick <= tick) {
               actor = candidate;
               actorId = entry.getKey();
            }
         }
         // Nearby players and non-swing causes cannot be uniquely attributed.
         if (nearby != 1) {
            candidate(victimId, tick, alternatives, "insufficient",
               "multiple or no nearby actors", Double.NaN);
            continue;
         }
         if (actor == null) {
            candidate(victimId, tick, alternatives, "insufficient",
               "no recent relayed swing", Double.NaN);
            continue;
         }
         if (!actor.stable() || !actor.at(0).survival
            || actor.at(0).ping < 0 || actor.at(0).ping > maximumPing) {
            candidate(victimId, tick, alternatives, "insufficient",
               "actor mode, position or ping quality", Double.NaN);
            continue;
         }
         double min = minimumPossibleDistance(actor, victim);
         if (!Double.isFinite(min)) {
            candidate(victimId, tick, alternatives, "insufficient",
               "nonfinite geometry", Double.NaN);
            continue;
         }
         double residual = min - LEGAL_REACH - observerSlack;
         if (residual <= grossResidual) {
            candidate(victimId, tick, alternatives, "plausible",
               "at least one legal trajectory fits the observer envelope", min);
            continue;
         }
         if (actor.lastReviewTick != Long.MIN_VALUE && tick - actor.lastReviewTick < 20) {
            candidate(victimId, tick, alternatives, "insufficient",
               "same fight episode as a prior candidate", min);
            continue;
         }
         actor.reachEpisodes = Math.min(20, actor.reachEpisodes + 1);
         actor.lastReviewTick = tick;
         String reason = "isolated swing/hurt candidate; both players nearly stationary for 5 ticks; "
            + "observer latency margin included; damage cause and server rules remain unknown";
         Evidence evidence = new Evidence(actorId, victimId, tick, "gross reach candidate",
            reason, min, residual, actor.at(0).ping, victim.at(0).ping,
            actor.reachEpisodes, nanos, history(actor), history(victim));
         emitted.add(evidence);
         candidate(victimId, tick, alternatives, "review candidate", reason, min);
         reviews.add(evidence);
         if (reviews.size() > MAX_REVIEWS) reviews.remove(0);
      }
      return emitted;
   }

   private void candidate(UUID victim, long tick, List<UUID> alternatives,
      String outcome, String reason, double distance) {
      candidates.add(new Candidate(victim, tick, alternatives, outcome, reason, distance));
      if (candidates.size() > MAX_REVIEWS) candidates.remove(0);
   }

   /** Cartesian history minimum favors every plausible legal position pairing. */
   private static double minimumPossibleDistance(Track actor, Track victim) {
      double minimum = Double.POSITIVE_INFINITY;
      for (int a = 0; a < Math.min(actor.size, 5); a++) {
         Sample source = actor.at(a);
         for (int v = 0; v < Math.min(victim.size, 5); v++) {
            Sample target = victim.at(v);
            minimum = Math.min(minimum, pairDistance(source, target));
         }
      }
      return minimum;
   }

   public static double replayMinimumDistance(Evidence evidence) {
      double minimum = Double.POSITIVE_INFINITY;
      for (Sample actor : evidence.actorHistory)
         for (Sample victim : evidence.victimHistory)
            minimum = Math.min(minimum, pairDistance(actor, victim));
      return minimum;
   }

   private static double pairDistance(Sample source, Sample target) {
      double dx = Math.max(0.0D, Math.abs(source.x - target.x) - 0.4D);
      double dz = Math.max(0.0D, Math.abs(source.z - target.z) - 0.4D);
      double eye = source.y + source.eyeHeight;
      double dy = Math.max(0.0D, Math.max(target.y - eye, eye - target.y - target.height));
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private static List<Sample> history(Track track) {
      List<Sample> copy = new ArrayList<Sample>(5);
      for (int i = 0; i < Math.min(track.size, 5); i++) copy.add(track.at(i));
      return copy;
   }

   private static double distance(Sample a, Sample b) {
      double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private static boolean finite(Sample sample) {
      return Double.isFinite(sample.x) && Double.isFinite(sample.y)
         && Double.isFinite(sample.z) && Float.isFinite(sample.yaw)
         && Float.isFinite(sample.pitch) && sample.height > 0.0D;
   }

   public List<Evidence> reviews() {
      return Collections.unmodifiableList(new ArrayList<Evidence>(reviews));
   }

   public List<Candidate> candidates() {
      return Collections.unmodifiableList(new ArrayList<Candidate>(candidates));
   }

   public boolean hasContinuousQuality(UUID id) {
      Track track = tracks.get(id);
      if (track == null || track.size < 5 || track.lastObservedTick != lastTick) return false;
      Sample sample = track.at(0);
      return !sample.riding && sample.survival
         && sample.ping >= 0 && sample.ping <= maximumPing;
   }

   public void clear() {
      tracks.clear();
      reviews.clear();
      candidates.clear();
      lastTick = Long.MIN_VALUE;
      lastNanos = 0L;
   }
}
