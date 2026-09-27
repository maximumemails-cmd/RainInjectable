package first.rain.anticheat.util.anticheat.checks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import first.rain.anticheat.Rain;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.anticheat.AlertManager;
import first.rain.anticheat.util.anticheat.PlayerEligibility;

/** Repeated block/swing overlap during observed combat. Each overlap is one
 * episode rather than many tick votes from the same animation. */
public class AutoBlockCheck {
   private final Map<UUID, AutoBlockEvidence> states = new HashMap<UUID, AutoBlockEvidence>();

   public void anticheatCheck(EntityPlayer player) {
      if (!cfg.v.detectAutoBlock) { reset(); return; }
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         forgetPlayer(player == null ? null : player.func_110124_au());
         return;
      }
      UUID uuid = player.func_110124_au();
      AutoBlockEvidence st = states.get(uuid);
      if (st == null) { st = new AutoBlockEvidence(); states.put(uuid, st); }
      long tick = net.minecraft.client.Minecraft.func_71410_x().field_71441_e.func_82737_E();
      boolean swing = player.field_82175_bq;
      boolean block = player.func_70632_aY();
      boolean combat = Rain.ANTICHEAT.killauraCheck.hasRecentCombat(uuid, tick);
      if (st.observe(tick, swing, block, combat)) {
         if (cfg.v.debugMessages) System.out.println("[Rain] AutoBlock " + player.func_70005_c_()
            + " episodes=" + st.episodes() + " score=" + st.score());
         AlertManager.flag(player, AlertManager.CheckType.AUTO_BLOCK, st.score());
      }
   }

   public void reset() { states.clear(); }
   public void forgetPlayer(UUID uuid) { if (uuid != null) states.remove(uuid); }
   public void retainPlayers(Set<UUID> ids) { states.keySet().retainAll(ids); }
}
