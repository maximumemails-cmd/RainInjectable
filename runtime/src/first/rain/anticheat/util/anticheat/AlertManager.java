package first.rain.anticheat.util.anticheat;

import first.rain.anticheat.Rain;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;

/**
 * Central sink for check flags. The first flag marks a player and emits one
 * chat/flash alert. Later failures are intentionally silent; the Tab-list
 * marker becomes the reminder.
 */
public final class AlertManager {
   public enum CheckType {
      AUTO_BLOCK("AutoBlock"),
      LEGIT_SCAFFOLD("LegitScaffold"),
      KILLAURA("Killaura");

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

   private AlertManager() {
   }

   public static void flag(EntityPlayer player, CheckType check, int vl) {
      Minecraft mc = Minecraft.func_71410_x();
      if (player == null || mc.field_71441_e == null) {
         return;
      }
      if (!PlayerEligibility.shouldCheckPlayer(player)) {
         forgetPlayer(player.func_110124_au());
         return;
      }

      UUID uuid = player.func_110124_au();
      if (markedPlayers.containsKey(uuid)) {
         return;
      }

      markedPlayers.put(uuid, new MarkedPlayer(uuid, player.func_70005_c_(), check, vl, mc.field_71441_e.func_82737_E()));
      Rain.addMessage(
         EnumChatFormatting.DARK_GRAY + "[" + EnumChatFormatting.WHITE + "AntiCheat" + EnumChatFormatting.DARK_GRAY + "] "
            + EnumChatFormatting.WHITE + player.func_70005_c_()
            + EnumChatFormatting.GRAY + " flagged "
            + EnumChatFormatting.AQUA + check.displayName()
            + EnumChatFormatting.GRAY + " (VL: " + EnumChatFormatting.WHITE + vl + EnumChatFormatting.GRAY + ")");
      FlashNotification.trigger();
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
      markedPlayers.clear();
   }

   public static void forgetPlayer(UUID uuid) {
      if (uuid != null) {
         markedPlayers.remove(uuid);
      }
   }

   public static void retainPlayers(Set<UUID> playerIds) {
      // Marked players intentionally survive until world unload, even if they
      // temporarily leave tab or stop being checkable.
   }
}
