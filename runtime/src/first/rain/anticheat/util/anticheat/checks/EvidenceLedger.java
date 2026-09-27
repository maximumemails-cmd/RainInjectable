package first.rain.anticheat.util.anticheat.checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** An ordinal review index. Units are deliberately not probabilities. */
public final class EvidenceLedger {
   public enum State { INSUFFICIENT, WATCH, REVIEW }
   public static final class Fact {
      public final UUID player;
      public final long tick;
      public final String group, hypothesis, explanation;
      public final int units;
      private Fact(UUID player, long tick, String group, String hypothesis,
         String explanation, int units) {
         this.player = player; this.tick = tick; this.group = group;
         this.hypothesis = hypothesis; this.explanation = explanation; this.units = units;
      }
   }
   private static final class Subject {
      final Map<String, Long> lastGroupTick = new HashMap<String, Long>();
      int index, independentEpisodes, cleanExposure;
      long lastTick = Long.MIN_VALUE;
      long lastEpisodeTick = Long.MIN_VALUE;
   }
   private final Map<UUID, Subject> subjects = new HashMap<UUID, Subject>();
   private final List<Fact> facts = new ArrayList<Fact>();

   /** Max one contribution per dependency group in a 20-tick fight window. */
   public State add(UUID player, long tick, String group, String hypothesis,
      String explanation, int units) {
      if (player == null || group == null || units < 1 || units > 3) return State.INSUFFICIENT;
      Subject subject = subjects.get(player);
      if (subject == null) { subject = new Subject(); subjects.put(player, subject); }
      Long previous = subject.lastGroupTick.get(group);
      if (previous != null && tick >= previous && tick - previous < 20) return state(subject);
      subject.lastGroupTick.put(group, tick);
      subject.index = Math.min(12, subject.index + units);
      if (subject.lastEpisodeTick == Long.MIN_VALUE || tick - subject.lastEpisodeTick >= 20) {
         subject.independentEpisodes = Math.min(12, subject.independentEpisodes + 1);
         subject.lastEpisodeTick = tick;
      }
      subject.cleanExposure = 0;
      Fact fact = new Fact(player, tick, group, hypothesis, explanation, units);
      facts.add(fact);
      if (facts.size() > 64) facts.remove(0);
      return state(subject);
   }

   /** Decay only when the player is observed in usable conditions. */
   public void cleanExposure(UUID player, long tick) {
      Subject subject = subjects.get(player);
      if (subject == null || tick <= subject.lastTick) return;
      subject.lastTick = tick;
      if (++subject.cleanExposure >= 40) {
         subject.cleanExposure = 0;
         subject.index = Math.max(0, subject.index - 1);
         if (subject.index == 0) subject.independentEpisodes = 0;
      }
   }

   public State state(UUID player) {
      return state(subjects.get(player));
   }

   private static State state(Subject subject) {
      if (subject == null || subject.index == 0) return State.INSUFFICIENT;
      return subject.index >= 5 && subject.independentEpisodes >= 2
         ? State.REVIEW : State.WATCH;
   }

   public List<Fact> facts() { return Collections.unmodifiableList(new ArrayList<Fact>(facts)); }
   public void retain(Set<UUID> ids) {
      subjects.keySet().retainAll(ids);
      facts.removeIf(fact -> !ids.contains(fact.player));
   }
   public void forget(UUID id) {
      subjects.remove(id);
      facts.removeIf(fact -> fact.player.equals(id));
   }
   public void clear() { subjects.clear(); facts.clear(); }
}
