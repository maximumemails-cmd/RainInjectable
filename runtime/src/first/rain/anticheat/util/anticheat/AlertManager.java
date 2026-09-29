package first.rain.anticheat.util.anticheat;

import first.rain.anticheat.Rain;
import first.rain.anticheat.RainCore;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import first.rain.anticheat.util.anticheat.checks.EvidenceLedger;
import first.rain.anticheat.util.anticheat.checks.EvidenceExporter;
import first.rain.anticheat.util.anticheat.checks.ObservationEngine;
import java.io.File;
import java.io.IOException;

/**
 * Alert history is separate from active detector state. Repeated failures are
 * logged for diagnostics while chat and flash remain quiet for marked players.
 */
public final class AlertManager {
   public enum CheckType {
      AUTO_BLOCK("block-use overlap"),
      LEGIT_SCAFFOLD("bridging rhythm"),
      KILLAURA("aim pattern");

      private final String displayName;

      CheckType(String displayName) {
         this.displayName = displayName;
      }

      public String displayName() {
         return this.displayName;
      }
   }

   public static final class MarkedPlayer {
      public final UUID uuid;
      public final String name;
      public final CheckType check;
      public final int vl;
      public final long tick;

      private MarkedPlayer(UUID uuid, String name, CheckType check, int vl, long tick) {
         this.uuid = uuid;
         this.name = name;
         this.check = check;
         this.vl = vl;
         this.tick = tick;
      }
   }

   private static final Map<UUID, MarkedPlayer> markedPlayers = new HashMap<UUID, MarkedPlayer>();
   private static final long REPEAT_LOG_TICKS = 200L;
   private static final Map<UUID, Long> lastReviewMessage = new HashMap<UUID, Long>();
   private static final EvidenceLedger ledger = new EvidenceLedger();

   private AlertManager() {
   }

   public static void recordLegacy(EntityPlayer player, CheckType check, int vl,
      String measurements) {
      recordLegacy(player, check, vl, measurements, 0.25D);
   }

   public static void recordLegacy(EntityPlayer player, CheckType check, int vl,
      String measurements, double strength) {
      // Legacy checks have no measured false-positive rate. Keep their signal
      // visible as review evidence without a red nametag or confirmed label.
      String group = check == CheckType.LEGIT_SCAFFOLD ? "bridging rhythm" : "combat proxies";
      review(player, group, check.displayName(), "legacy pattern score=" + vl
         + "; " + measurements + "; attack/placement identity is inferred",
         1, strength, 0.55D);
   }

   public static void review(EntityPlayer player, String group, String hypothesis,
      String explanation, int units) {
      review(player, group, hypothesis, explanation, units, 0.35D, 0.65D);
   }

   public static void review(EntityPlayer player, String group, String hypothesis,
      String explanation, int units, double strength, double reliability) {
      if (!RainCore.isEnabled()) {
         return;
      }
      Minecraft mc = Minecraft.func_71410_x();
      if (player == null || mc.field_71441_e == null) {
         return;
      }
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         forgetPlayer(player.func_110124_au());
         return;
      }

      UUID uuid = player.func_110124_au();
      long tick = mc.field_71441_e.func_82737_E();
      EvidenceLedger.State state = ledger.add(uuid, tick, group, hypothesis, explanation,
         units, strength, reliability);
      if (state != EvidenceLedger.State.REVIEW) return;
      Long last = lastReviewMessage.get(uuid);
      if (last != null && tick >= last && tick - last < REPEAT_LOG_TICKS) return;
      lastReviewMessage.put(uuid, tick);
      Rain.addMessage(
         EnumChatFormatting.DARK_GRAY + "[" + EnumChatFormatting.WHITE + "Rain" + EnumChatFormatting.DARK_GRAY + "] "
            + EnumChatFormatting.WHITE + player.func_70005_c_() + EnumChatFormatting.GRAY + " • "
            + EnumChatFormatting.AQUA + hypothesis + EnumChatFormatting.GRAY + " • "
            + EnumChatFormatting.WHITE + ledger.confidence(uuid) + "%"
            + EnumChatFormatting.GRAY + " evidence confidence");
      FlashNotification.trigger();
      if (first.rain.anticheat.config.cfg.v.debugMessages) {
         System.out.println("[Rain] review " + player.func_110124_au() + " " + hypothesis + ": " + explanation);
      }
   }

   public static boolean isMarked(UUID uuid) {
      return uuid != null && markedPlayers.containsKey(uuid);
   }

   public static boolean hasMarkedPlayers() {
      return !markedPlayers.isEmpty();
   }

   public static Set<UUID> markedPlayerIds() {
      return Collections.unmodifiableSet(new HashSet<UUID>(markedPlayers.keySet()));
   }

   /** Drop all marked players. Call on world change / disconnect. */
   public static void clear() {
      FlashEffect.clear();
      markedPlayers.clear();
      lastReviewMessage.clear();
      ledger.clear();
   }

   public static void forgetPlayer(UUID uuid) {
      if (uuid != null) {
         markedPlayers.remove(uuid);
         lastReviewMessage.remove(uuid);
         ledger.forget(uuid);
      }
   }

   public static void retainPlayers(Set<UUID> playerIds) {
      markedPlayers.keySet().retainAll(playerIds);
      lastReviewMessage.keySet().retainAll(playerIds);
      ledger.retain(playerIds);
   }

   public static void cleanExposure(UUID player, long tick) { ledger.cleanExposure(player, tick); }

   public static java.util.List<EvidenceLedger.Fact> evidence() { return ledger.facts(); }

   public static void exportEvidence(File file, ObservationEngine observations) throws IOException {
      EvidenceExporter.write(file, observations, ledger);
   }
}
