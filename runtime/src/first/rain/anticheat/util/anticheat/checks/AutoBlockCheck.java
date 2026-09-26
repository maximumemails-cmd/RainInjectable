package first.rain.anticheat.util.anticheat.checks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import first.rain.anticheat.Rain;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.anticheat.AlertManager;
import first.rain.anticheat.util.anticheat.PlayerEligibility;

public class AutoBlockCheck {
   private static final int FAIL_TICKS = 10;

   private final Map<UUID, Integer> autoBlockTicks = new HashMap<UUID, Integer>();

   public void anticheatCheck(EntityPlayer player) {
      if (!cfg.v.detectAutoBlock) {
         return;
      }
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         this.forgetPlayer(player == null ? null : player.func_110124_au());
         return;
      }

      UUID uuid = player.func_110124_au();
      if (player.field_82175_bq && player.func_70632_aY()) {
         int ticks = this.autoBlockTicks.getOrDefault(uuid, 0) + 1;
         this.autoBlockTicks.put(uuid, ticks);
         if (cfg.v.debugMessages && ticks > 5) {
            Rain.addMessage(EnumChatFormatting.YELLOW + "[AntiCheat]: " + EnumChatFormatting.WHITE + player.func_70005_c_() + " AutoBlock ticks: " + ticks);
         }

         if (ticks > FAIL_TICKS) {
            AlertManager.flag(player, AlertManager.CheckType.AUTO_BLOCK, ticks);
         }
      } else {
         this.autoBlockTicks.remove(uuid);
      }
   }

   /** True while any tracked player is currently over the failure threshold. */
   public boolean failedAutoBlock() {
      for (int ticks : this.autoBlockTicks.values()) {
         if (ticks > FAIL_TICKS) {
            return true;
         }
      }
      return false;
   }

   public void reset() {
      this.autoBlockTicks.clear();
   }

   public void forgetPlayer(UUID uuid) {
      if (uuid != null) {
         this.autoBlockTicks.remove(uuid);
      }
   }

   public void retainPlayers(Set<UUID> playerIds) {
      this.autoBlockTicks.keySet().retainAll(playerIds);
   }
}
