package first.rain.anticheat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import first.rain.anticheat.util.anticheat.AntiCheatData;
import first.rain.anticheat.util.anticheat.PlayerEligibility;

/**
 * Client tick entry point shared by the Forge mod and injected runtime.
 */
@Mod(modid = Rain.MODID, name = "Rain", version = Rain.VERSION)
public class Rain {
   public static final String MODID = "rain";
   public static final String VERSION = "1.2.0-injectable";

   public static final AntiCheatData ANTICHEAT = new AntiCheatData();
   private net.minecraft.world.World lastWorld;
   private long lastTick = Long.MIN_VALUE;

   @Mod.EventHandler
   public void init(FMLInitializationEvent event) {
      RainCore.start("mod");
   }

   /** Prints a client-side chat line (not sent to the server). */
   public static void addMessage(String message) {
      Minecraft mc = Minecraft.func_71410_x();
      if (mc.field_71439_g != null) {
         mc.field_71456_v.func_146158_b().func_146227_a(new ChatComponentText(message)); // ingameGUI.getChatGUI().printChatMessage
      }
   }

   @SubscribeEvent
   public void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.func_71410_x();
      if (mc.field_71441_e != lastWorld) {
         ANTICHEAT.clearAll();
         lastWorld = mc.field_71441_e;
         lastTick = Long.MIN_VALUE;
      }
      if (!RainCore.isEnabled()) return;
      if (mc.field_71441_e == null || mc.field_71439_g == null) {
         return;
      }
      long tick = mc.field_71441_e.func_82737_E();
      if (tick == lastTick) return;
      lastTick = tick;
      List<?> players = mc.field_71441_e.field_73010_i; // playerEntities
      Set<UUID> realPlayerIds = new HashSet<UUID>();
      Set<UUID> checkablePlayerIds = new HashSet<UUID>();
      List<EntityPlayer> checkablePlayers = new ArrayList<EntityPlayer>();
      for (Object obj : players) {
         if (!(obj instanceof EntityPlayer)) {
            continue;
         }
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
      for (EntityPlayer player : checkablePlayers) {
         ANTICHEAT.anticheatCheck(player);
      }
   }

   @SubscribeEvent
   public void onWorldUnload(WorldEvent.Unload event) {
      if (event.world != lastWorld) return;
      ANTICHEAT.clearAll();
      lastWorld = null;
      lastTick = Long.MIN_VALUE;
   }
}
