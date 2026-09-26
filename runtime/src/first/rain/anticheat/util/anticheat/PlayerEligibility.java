package first.rain.anticheat.util.anticheat;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldSettings.GameType;

/**
 * Shared player gate for anticheat checks. Servers can spawn NPCs/bots as
 * EntityPlayer instances, so Java type alone is not proof that the entity is a
 * real connected player.
 */
public final class PlayerEligibility {
   private PlayerEligibility() {
   }

   /** True for real players, including the local client player. */
   public static boolean isRealPlayer(EntityPlayer player) {
      Minecraft mc = Minecraft.func_71410_x();
      if (player == null || mc == null || mc.field_71441_e == null || player.field_70128_L) {
         return false;
      }
      if (player.func_110143_aJ() <= 0.0F || player.func_175149_v()) {
         return false;
      }

      NetHandlerPlayClient netHandler = mc.func_147114_u();
      if (netHandler == null) {
         return false;
      }

      UUID uuid = player.func_110124_au();
      if (uuid == null) {
         return false;
      }

      NetworkPlayerInfo info = netHandler.func_175102_a(uuid);
      if (info == null || info.func_178848_b() == GameType.SPECTATOR) {
         return false;
      }

      String name = player.func_70005_c_();
      if (name == null || name.length() == 0) {
         return false;
      }
      return netHandler.func_175104_a(name) == info;
   }

   /** True for remote players that should be analyzed as suspects. */
   public static boolean shouldCheckPlayer(EntityPlayer player) {
      Minecraft mc = Minecraft.func_71410_x();
      return isRealPlayer(player) && mc != null && mc.field_71439_g != null && player != mc.field_71439_g;
   }

   /** True for real players that can be used as killaura geometry targets. */
   public static boolean shouldUseAsTarget(EntityPlayer candidate, EntityPlayer attacker) {
      return candidate != attacker && isRealPlayer(candidate);
   }
}
