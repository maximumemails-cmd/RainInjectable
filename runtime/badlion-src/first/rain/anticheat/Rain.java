package first.rain.anticheat;

import first.rain.anticheat.util.anticheat.AntiCheatData;
import first.rain.anticheat.util.anticheat.PlayerEligibility;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

/** Badlion entry point for Rain's detector logic, without Forge events. */
public final class Rain {
   public static final AntiCheatData ANTICHEAT = new AntiCheatData();
   private static net.minecraft.world.World lastWorld;
   private static long lastTick = Long.MIN_VALUE;
   public static volatile boolean inWorld;
   public static volatile int eligiblePlayers;
   public static volatile long processedTicks;

   private Rain() {}

   public static void addMessage(String message) {
      Minecraft mc = Minecraft.func_71410_x();
      if (mc != null && mc.field_71439_g != null) {
         mc.field_71456_v.func_146158_b().func_146227_a(new ChatComponentText(message));
      }
   }

   /** Called on the Minecraft thread. Run each detector once per world tick. */
   public static void tick() {
      Minecraft mc = Minecraft.func_71410_x();
      if (mc == null) return;
      if (mc.field_71441_e != lastWorld) {
         ANTICHEAT.clearAll();
         lastWorld = mc.field_71441_e;
         lastTick = Long.MIN_VALUE;
      }
      inWorld = mc.field_71441_e != null && mc.field_71439_g != null;
      if (!inWorld) eligiblePlayers = 0;
      if (!RainCore.isEnabled() || mc.field_71441_e == null || mc.field_71439_g == null) return;
      long tick = mc.field_71441_e.func_82737_E();
      if (tick == lastTick) return;
      lastTick = tick;

      List<?> players = mc.field_71441_e.field_73010_i;
      Set<UUID> realPlayerIds = new HashSet<UUID>();
      Set<UUID> checkablePlayerIds = new HashSet<UUID>();
      List<EntityPlayer> checkablePlayers = new ArrayList<EntityPlayer>();
      for (Object obj : players) {
         if (!(obj instanceof EntityPlayer)) continue;
         EntityPlayer player = (EntityPlayer)obj;
         if (!PlayerEligibility.isRealPlayer(player)) continue;
         realPlayerIds.add(player.func_110124_au());
         if (player != mc.field_71439_g) {
            checkablePlayerIds.add(player.func_110124_au());
            checkablePlayers.add(player);
         }
      }
      ANTICHEAT.retainPlayers(checkablePlayerIds, realPlayerIds);
      ANTICHEAT.observeTick(checkablePlayers, mc.field_71439_g, tick);
      for (EntityPlayer player : checkablePlayers) ANTICHEAT.anticheatCheck(player);
      eligiblePlayers = checkablePlayers.size();
      processedTicks++;
   }
}
