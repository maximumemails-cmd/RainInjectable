package first.rain.anticheat.util.anticheat;

import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import first.rain.anticheat.util.anticheat.checks.AutoBlockCheck;
import first.rain.anticheat.util.anticheat.checks.KillauraCheck;
import first.rain.anticheat.util.anticheat.checks.LegitScaffoldCheck;

public class AntiCheatData {
   public AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
   public LegitScaffoldCheck legitScaffoldCheck = new LegitScaffoldCheck();
   public KillauraCheck killauraCheck = new KillauraCheck();

   public void anticheatCheck(EntityPlayer player) {
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         this.forgetPlayer(player);
         return;
      }
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
      AlertManager.forgetPlayer(uuid);
   }

   /** Drop all per-player tracking and alert cooldowns. Call on world change / disconnect. */
   public void clearAll() {
      this.autoBlockCheck.reset();
      this.legitScaffoldCheck.reset();
      this.killauraCheck.reset();
      AlertManager.clear();
   }
}
