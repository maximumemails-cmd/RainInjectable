package first.rain.anticheat.util.anticheat.checks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** User-triggered, local JSONL export with session-local player aliases. */
public final class EvidenceExporter {
   private EvidenceExporter() {}

   public static void write(File file, ObservationEngine observations, EvidenceLedger ledger)
      throws IOException {
      Map<UUID, String> aliases = new HashMap<UUID, String>();
      File parent = file.getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs())
         throw new IOException("Could not create evidence directory");
      try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
         new FileOutputStream(file), StandardCharsets.UTF_8))) {
         out.write("{\"schema\":1,\"source\":\"tick-interpolated-entity\",\"note\":\"Passive observations; actor and damage cause are inferred\"}\n");
         for (ObservationEngine.Candidate candidate : observations.candidates()) {
            StringBuilder row = new StringBuilder("{\"type\":\"candidate\",\"tick\":")
               .append(candidate.tick).append(",\"victim\":\"")
               .append(alias(aliases, candidate.victim)).append("\",\"actors\":[");
            for (int i = 0; i < candidate.nearbyActors.size(); i++) {
               if (i > 0) row.append(',');
               row.append('"').append(alias(aliases, candidate.nearbyActors.get(i))).append('"');
            }
            row.append("],\"outcome\":\"").append(escape(candidate.outcome))
               .append("\",\"reason\":\"").append(escape(candidate.reason)).append('"');
            if (Double.isFinite(candidate.minimumDistance))
               row.append(",\"minimumDistance\":").append(candidate.minimumDistance);
            out.write(row.append("}\n").toString());
         }
         for (ObservationEngine.Evidence evidence : observations.reviews()) {
            String actor = alias(aliases, evidence.actor);
            String victim = alias(aliases, evidence.victim);
            out.write("{\"type\":\"reach\",\"tick\":" + evidence.tick
               + ",\"nanos\":" + evidence.monotonicNanos + ",\"actor\":\"" + actor
               + "\",\"victim\":\"" + victim + "\",\"minimumDistance\":"
               + evidence.minimumDistance + ",\"residual\":" + evidence.residual
               + ",\"actorPing\":" + evidence.actorPing + ",\"victimPing\":"
               + evidence.victimPing + ",\"independentEpisodes\":" + evidence.independentEpisodes
               + ",\"reason\":\"" + escape(evidence.reason) + "\"}\n");
            writeHistory(out, actor, evidence.tick, evidence.actorHistory);
            writeHistory(out, victim, evidence.tick, evidence.victimHistory);
         }
         for (EvidenceLedger.Fact fact : ledger.facts()) {
            out.write("{\"type\":\"fact\",\"tick\":" + fact.tick
               + ",\"player\":\"" + alias(aliases, fact.player) + "\",\"group\":\""
               + escape(fact.group) + "\",\"hypothesis\":\""
               + escape(fact.hypothesis) + "\",\"explanation\":\""
               + escape(fact.explanation) + "\",\"units\":" + fact.units + "}\n");
         }
      }
   }

   private static void writeHistory(BufferedWriter out, String player, long eventTick,
      java.util.List<ObservationEngine.Sample> history) throws IOException {
      for (int age = 0; age < history.size(); age++) {
         ObservationEngine.Sample s = history.get(age);
         out.write("{\"type\":\"sample\",\"player\":\"" + player
            + "\",\"eventTick\":" + eventTick + ",\"age\":" + age
            + ",\"x\":" + s.x + ",\"y\":" + s.y + ",\"z\":" + s.z
            + ",\"eyeHeight\":" + s.eyeHeight + ",\"height\":" + s.height
            + ",\"yaw\":" + s.yaw + ",\"pitch\":" + s.pitch
            + ",\"swing\":" + s.swing + ",\"hurt\":" + s.hurt
            + ",\"tabPing\":" + s.ping + ",\"survival\":" + s.survival + "}\n");
      }
   }

   private static String alias(Map<UUID, String> aliases, UUID id) {
      String existing = aliases.get(id);
      if (existing != null) return existing;
      String created = "P" + (aliases.size() + 1);
      aliases.put(id, created);
      return created;
   }

   private static String escape(String value) {
      return value.replace("\\", "\\\\").replace("\"", "\\\"")
         .replace("\n", "\\n").replace("\r", "\\r");
   }
}
