import first.rain.anticheat.util.anticheat.checks.EvidenceLedger;
import first.rain.anticheat.util.anticheat.checks.EvidenceExporter;
import first.rain.anticheat.util.anticheat.checks.ObservationEngine;
import java.util.Collections;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public final class EvidenceHarness {
   private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
   private static void check(boolean condition, String message) {
      if (!condition) throw new AssertionError(message);
   }
   public static void main(String[] args) {
      EvidenceLedger ledger = new EvidenceLedger();
      check(ledger.add(PLAYER, 10, "combat proxies", "aim", "inferred hit", 1)
         == EvidenceLedger.State.WATCH, "one weak signal watches");
      check(ledger.add(PLAYER, 11, "combat proxies", "block", "same inferred hit", 1)
         == EvidenceLedger.State.WATCH, "shared attribution does not double count");
      check(ledger.facts().size() == 1, "dependent fact capped");
      check(ledger.add(PLAYER, 12, "geometry", "reach", "wide residual", 3)
         == EvidenceLedger.State.WATCH, "same fight groups do not create independent episodes");
      check(ledger.add(PLAYER, 35, "geometry", "reach", "second wide residual", 3)
         == EvidenceLedger.State.REVIEW, "independent episodes reach review");
      EvidenceLedger onlyGeometry = new EvidenceLedger();
      check(onlyGeometry.add(PLAYER, 1, "geometry", "reach", "first", 3)
         == EvidenceLedger.State.WATCH, "first reach remains watch");
      check(onlyGeometry.add(PLAYER, 22, "geometry", "reach", "second", 3)
         == EvidenceLedger.State.REVIEW, "two separated gross reach episodes review");
      for (int tick = 36; tick <= 320; tick++) ledger.cleanExposure(PLAYER, tick);
      check(ledger.state(PLAYER) == EvidenceLedger.State.INSUFFICIENT,
         "clean observed exposure decays ordinal evidence");
      ledger.add(PLAYER, 400, "geometry", "reach", "new session", 3);
      try {
         Path output = Files.createTempFile("rain-evidence-harness", ".jsonl");
         try {
            EvidenceExporter.write(output.toFile(), new ObservationEngine(), ledger);
            String json = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            check(json.contains("\"type\":\"fact\""), "evidence exported");
            check(!json.contains(PLAYER.toString()), "export uses local aliases");
         } finally { Files.deleteIfExists(output); }
      } catch (java.io.IOException e) { throw new AssertionError(e); }
      ledger.retain(Collections.<UUID>emptySet());
      check(ledger.facts().isEmpty() && ledger.state(PLAYER) == EvidenceLedger.State.INSUFFICIENT,
         "despawn removes identifiers and facts");
      System.out.println("EVIDENCE HARNESS OK: 11 assertions");
   }
}
