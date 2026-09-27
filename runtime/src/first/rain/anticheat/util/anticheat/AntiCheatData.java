package first.rain.anticheat.util.anticheat;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldSettings.GameType;
import first.rain.anticheat.util.anticheat.checks.AutoBlockCheck;
import first.rain.anticheat.util.anticheat.checks.KillauraCheck;
import first.rain.anticheat.util.anticheat.checks.LegitScaffoldCheck;
import first.rain.anticheat.util.anticheat.checks.ObservationEngine;
import first.rain.anticheat.config.cfg;
import java.io.File;
import java.io.IOException;

public class AntiCheatData {
   public AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
   public LegitScaffoldCheck legitScaffoldCheck = new LegitScaffoldCheck();
   public KillauraCheck killauraCheck = new KillauraCheck();
   public final ObservationEngine observations = new ObservationEngine();

   public void exportEvidence(File file) throws IOException {
      AlertManager.exportEvidence(file, observations);
   }

   /** Collect a complete tick before any legacy check interprets combat. */
   public void observeTick(List<EntityPlayer> players, EntityPlayer local, long tick) {
      observations.configure(cfg.v.reviewGrossReach, cfg.v.maxObserverPingMs,
         cfg.v.reachObserverSlack, cfg.v.reachGrossResidual);
      List<ObservationEngine.Sample> batch = new ArrayList<ObservationEngine.Sample>(players.size() + 1);
      batch.add(sample(local));
      for (EntityPlayer player : players) batch.add(sample(player));
      for (ObservationEngine.Evidence evidence : observations.observe(tick, System.nanoTime(), batch)) {
         for (EntityPlayer player : players) {
            if (player.func_110124_au().equals(evidence.actor)) {
               AlertManager.review(player, "geometry", "gross reach candidate", evidence.reason
                  + "; minimum distance=" + String.format(java.util.Locale.ROOT, "%.2f", evidence.minimumDistance)
                  + "; residual=" + String.format(java.util.Locale.ROOT, "%.2f", evidence.residual)
                  + "; episodes=" + evidence.independentEpisodes, 3);
               break;
            }
         }
      }
   }

   private static ObservationEngine.Sample sample(EntityPlayer player) {
      NetworkPlayerInfo info = Minecraft.func_71410_x().func_147114_u().func_175102_a(player.func_110124_au());
      int ping = info == null ? -1 : info.func_178853_c();
      return new ObservationEngine.Sample(player.func_110124_au(), player.field_70165_t,
         player.field_70163_u, player.field_70161_v, player.func_70047_e(),
         player.field_70131_O, player.field_70177_z, player.field_70125_A,
         player.field_82175_bq, player.field_70737_aN, ping, player.field_70154_o != null,
         info != null && info.func_178848_b() == GameType.SURVIVAL);
   }

   public void anticheatCheck(EntityPlayer player) {
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         this.forgetPlayer(player);
         return;
      }
      if (!observations.hasContinuousQuality(player.func_110124_au())) {
         UUID id = player.func_110124_au();
         this.autoBlockCheck.forgetPlayer(id);
         this.legitScaffoldCheck.forgetPlayer(id);
         this.killauraCheck.forgetPlayer(id);
         return;
      }
      AlertManager.cleanExposure(player.func_110124_au(), Minecraft.func_71410_x().field_71441_e.func_82737_E());
      // Combat correlation must be updated before checks that consume it.
      this.killauraCheck.anticheatCheck(player);
      this.autoBlockCheck.anticheatCheck(player);
      this.legitScaffoldCheck.anticheatCheck(player);
   }

   /** Keep only currently eligible players in per-player anticheat state. */
   public void retainPlayers(Set<UUID> checkablePlayerIds, Set<UUID> realPlayerIds) {
      this.autoBlockCheck.retainPlayers(checkablePlayerIds);
      this.legitScaffoldCheck.retainPlayers(checkablePlayerIds);
      this.killauraCheck.retainPlayers(checkablePlayerIds, realPlayerIds);
      AlertManager.retainPlayers(realPlayerIds);
   }

   /** Drop all state for a player that is no longer eligible. */
   public void forgetPlayer(EntityPlayer player) {
      if (player == null) {
         return;
      }
      UUID uuid = player.func_110124_au();
      this.autoBlockCheck.forgetPlayer(uuid);
      this.legitScaffoldCheck.forgetPlayer(uuid);
      this.killauraCheck.forgetPlayer(uuid);
      // The next complete batch drops this UUID from observation history.
      AlertManager.forgetPlayer(uuid);
   }

   /** Drop all per-player tracking and alert cooldowns. Call on world change / disconnect. */
   public void clearAll() {
      this.autoBlockCheck.reset();
      this.legitScaffoldCheck.reset();
      this.killauraCheck.reset();
      this.observations.clear();
      AlertManager.clear();
   }
}
