package first.rain.anticheat.util.anticheat.checks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.anticheat.AlertManager;
import first.rain.anticheat.util.anticheat.PlayerEligibility;

/** Looks for repeated, unusually regular short crouches during plausible
 * bridging. A single fast crouch or block swing never flags a player. */
public class LegitScaffoldCheck {
   private final Map<UUID, ScaffoldEvidence> states = new HashMap<UUID, ScaffoldEvidence>();

   public void anticheatCheck(EntityPlayer player) {
      if (!cfg.v.detectLegitScaffold) { reset(); return; }
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         forgetPlayer(player == null ? null : player.func_110124_au());
         return;
      }
      UUID id = player.func_110124_au();
      ScaffoldEvidence st = states.get(id);
      if (st == null) { st = new ScaffoldEvidence(); states.put(id, st); }
      long tick = player.field_70173_aa;
      boolean sneak = player.func_70093_af();
      boolean swing = player.field_82175_bq;
      boolean block = player.func_70694_bm() != null
         && player.func_70694_bm().func_77973_b() instanceof ItemBlock;
      double dx = player.field_70165_t - player.field_70142_S;
      double dz = player.field_70161_v - player.field_70136_U;
      double speedSq = dx * dx + dz * dz;
      ObservationEngine.Sample observation = first.rain.anticheat.Rain.ANTICHEAT.observations.latest(id);
      boolean clearEdge = observation != null && !observation.uncertainEnvironment
         && Double.isFinite(observation.edgeDistance) && observation.edgeDistance <= 0.32D;
      boolean context = block && player.field_70122_E && player.field_70125_A >= 60.0F
         && speedSq >= 0.0036D && speedSq <= 0.25D && clearEdge;
      if (st.observe(tick, sneak, swing, context)) {
         if (cfg.v.debugMessages) System.out.println("[Rain] Scaffold " + player.func_70005_c_()
            + " regular=" + st.regularEpisodes() + " episodes=" + st.episodes()
            + " score=" + st.score());
         AlertManager.recordLegacy(player, AlertManager.CheckType.LEGIT_SCAFFOLD, st.score(),
            "regularCrouches=" + st.regularEpisodes() + ", rhythmEpisodes=" + st.episodes(),
            Math.min(0.5D, 0.12D + 0.04D * st.regularEpisodes()));
      }
   }

   public void reset() { states.clear(); }
   public void forgetPlayer(UUID uuid) { if (uuid != null) states.remove(uuid); }
   public void retainPlayers(Set<UUID> ids) { states.keySet().retainAll(ids); }
}
