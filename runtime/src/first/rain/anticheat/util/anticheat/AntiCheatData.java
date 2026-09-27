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
import first.rain.anticheat.util.anticheat.checks.TemporalAnalysis;
import first.rain.anticheat.config.cfg;
import java.io.File;
import java.io.IOException;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.potion.Potion;

public class AntiCheatData {
   public AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
   public LegitScaffoldCheck legitScaffoldCheck = new LegitScaffoldCheck();
   public KillauraCheck killauraCheck = new KillauraCheck();
   public final ObservationEngine observations = new ObservationEngine();
   public final TemporalAnalysis temporal = new TemporalAnalysis();

   public void exportEvidence(File file) throws IOException {
      AlertManager.exportEvidence(file, observations);
   }

   /** Collect a complete tick before any legacy check interprets combat. */
   public void observeTick(List<EntityPlayer> players, EntityPlayer local, long tick) {
      observations.setTemporalEnabled(cfg.v.temporalAnalysis);
      observations.configure(cfg.v.reviewGrossReach, cfg.v.maxObserverPingMs,
         cfg.v.reachObserverSlack, cfg.v.reachGrossResidual);
      List<ObservationEngine.Sample> batch = new ArrayList<ObservationEngine.Sample>(players.size() + 1);
      batch.add(sample(local));
      for (EntityPlayer player : players) batch.add(sample(player));
      long nanos = System.nanoTime();
      if (cfg.v.temporalAnalysis) {
         for (TemporalAnalysis.Signal signal : temporal.observe(tick, nanos, batch)) {
            if (!cfg.v.detectLegitScaffold) continue;
            for (EntityPlayer player : players) if (player.func_110124_au().equals(signal.player)) {
               AlertManager.review(player, "bridging rhythm", signal.name, signal.explanation,
                  signal.strength >= 0.65D ? 3 : 2,
                  signal.strength, signal.reliability);
               break;
            }
         }
      } else temporal.clear();
      for (ObservationEngine.Evidence evidence : observations.observe(tick, nanos, batch)) {
         for (EntityPlayer player : players) {
            if (player.func_110124_au().equals(evidence.actor)) {
               AlertManager.review(player, "geometry", "gross reach candidate", evidence.reason
                  + "; minimum distance=" + String.format(java.util.Locale.ROOT, "%.2f", evidence.minimumDistance)
                  + "; residual=" + String.format(java.util.Locale.ROOT, "%.2f", evidence.residual)
                  + "; episodes=" + evidence.independentEpisodes, 3,
                  Math.min(1.0D, evidence.residual / 2.0D),
                  Math.max(0.45D, 1.0D - (evidence.actorPing + evidence.victimPing) / 600.0D));
               break;
            }
         }
      }
   }

   private static ObservationEngine.Sample sample(EntityPlayer player) {
      NetworkPlayerInfo info = Minecraft.func_71410_x().func_147114_u().func_175102_a(player.func_110124_au());
      int ping = info == null ? -1 : info.func_178853_c();
      boolean blockHeld = player.func_70694_bm() != null
         && player.func_70694_bm().func_77973_b() instanceof ItemBlock;
      boolean uncertain = player.field_70123_F || player.func_70090_H()
         || player.func_70617_f_() || player.func_70072_I()
         || player.func_70644_a(Potion.field_76424_c)
         || player.func_70644_a(Potion.field_76421_d)
         || player.func_70644_a(Potion.field_76430_j)
         || player.field_70737_aN > 0;
      double edge = blockHeld && !uncertain && player.field_70122_E
         ? edgeDistance(player) : Double.NaN;
      return new ObservationEngine.Sample(player.func_110124_au(), player.field_70165_t,
         player.field_70163_u, player.field_70161_v, player.func_70047_e(),
         player.field_70131_O, player.field_70177_z, player.field_70125_A,
         player.field_82175_bq, player.field_70737_aN, ping, player.field_70154_o != null,
         info != null && info.func_178848_b() == GameType.SURVIVAL,
         player.func_70093_af(), player.func_70051_ag(), blockHeld,
         player.field_70122_E, uncertain, edge);
   }

   /** Only full, supported blocks with an unsupported cell in the travel direction. */
   private static double edgeDistance(EntityPlayer p) {
      double dx = p.field_70165_t - p.field_70142_S;
      double dz = p.field_70161_v - p.field_70136_U;
      double speed = Math.sqrt(dx * dx + dz * dz);
      if (speed < 0.06D || speed > 0.55D) return Double.NaN;
      double x = p.field_70165_t, z = p.field_70161_v;
      int y = (int)Math.floor(p.field_70163_u - 0.1D);
      BlockPos here = new BlockPos((int)Math.floor(x), y, (int)Math.floor(z));
      Block below = p.field_70170_p.func_180495_p(here).func_177230_c();
      if (!below.func_149730_j()) return Double.NaN;
      double nx = dx / speed, nz = dz / speed;
      double aheadX = x + nx * 0.45D, aheadZ = z + nz * 0.45D;
      BlockPos ahead = new BlockPos((int)Math.floor(aheadX), y, (int)Math.floor(aheadZ));
      if (ahead.equals(here) || !p.field_70170_p.func_175623_d(ahead)) return Double.NaN;
      double fx = x - Math.floor(x), fz = z - Math.floor(z);
      double ex = nx > 0.15D ? 1.0D - fx : (nx < -0.15D ? fx : Double.POSITIVE_INFINITY);
      double ez = nz > 0.15D ? 1.0D - fz : (nz < -0.15D ? fz : Double.POSITIVE_INFINITY);
      return Math.min(ex, ez);
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
      this.temporal.retain(checkablePlayerIds);
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
      this.temporal.clear();
      AlertManager.clear();
   }
}
