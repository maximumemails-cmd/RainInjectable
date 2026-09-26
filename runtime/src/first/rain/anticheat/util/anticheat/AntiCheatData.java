package first.rain.anticheat.util.anticheat;

import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import first.rain.anticheat.util.anticheat.checks.AutoBlockCheck;
import first.rain.anticheat.util.anticheat.checks.KillauraCheck;
import first.rain.anticheat.util.anticheat.checks.LegitScaffoldCheck;

public class AntiCheatData {
   public PlayerData playerData = new PlayerData();
   public AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
   public LegitScaffoldCheck legitScaffoldCheck = new LegitScaffoldCheck();
   public KillauraCheck killauraCheck = new KillauraCheck();

   public void anticheatCheck(EntityPlayer player) {
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         this.forgetPlayer(player);
         return;
      }
      this.playerData.update(player);
      this.autoBlockCheck.anticheatCheck(player);
      this.legitScaffoldCheck.anticheatCheck(player);
      this.killauraCheck.anticheatCheck(player);
   }

   public boolean failedAutoBlock() {
      return this.autoBlockCheck.failedAutoBlock();
   }

   public boolean failedLegitScaffold() {
      return this.legitScaffoldCheck.failedLegitScaffold();
   }

   public boolean failedKillaura() {
      return this.killauraCheck.failedKillaura();
   }

   /** Keep only currently eligible players in per-player anticheat state. */
   public void retainPlayers(Set<UUID> checkablePlayerIds, Set<UUID> realPlayerIds) {
      this.autoBlockCheck.retainPlayers(checkablePlayerIds);
      this.legitScaffoldCheck.retainPlayers(checkablePlayerIds);
      this.killauraCheck.retainPlayers(checkablePlayerIds, realPlayerIds);
      AlertManager.retainPlayers(checkablePlayerIds);
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
      LegitScaffoldCheck.clear();
      AlertManager.clear();
   }
}
