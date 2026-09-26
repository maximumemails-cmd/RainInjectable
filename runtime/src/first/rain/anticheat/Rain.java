package first.rain.anticheat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import first.rain.anticheat.gui.ClickGuiKeybind;
import first.rain.anticheat.util.anticheat.AlertManager;
import first.rain.anticheat.util.anticheat.AntiCheatData;
import first.rain.anticheat.util.anticheat.FlashNotification;
import first.rain.anticheat.util.anticheat.NametagOverlayRenderer;
import first.rain.anticheat.util.anticheat.PlayerEligibility;

/**
 * Main mod class. If you have your original Rain.java, replace this file
 * with it — the rest of the code only depends on addMessage(String) existing
 * and anticheatCheck(EntityPlayer) being called once per player per tick.
 */
@Mod(modid = Rain.MODID, name = "Rain", version = Rain.VERSION)
public class Rain {
   public static final String MODID = "rain";
   public static final String VERSION = "1.0";

   public static final AntiCheatData ANTICHEAT = new AntiCheatData();

   @Mod.EventHandler
   public void init(FMLInitializationEvent event) {
      ClickGuiKeybind.register();
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.register(new FlashNotification());
      MinecraftForge.EVENT_BUS.register(new NametagOverlayRenderer());
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
      if (mc.field_71441_e == null || mc.field_71439_g == null) {
         return;
      }
      List<?> players = mc.field_71441_e.field_73010_i; // playerEntities
      Set<UUID> realPlayerIds = new HashSet<UUID>();
      Set<UUID> checkablePlayerIds = new HashSet<UUID>();
      for (Object obj : players) {
         if (!(obj instanceof EntityPlayer)) {
            continue;
         }
         EntityPlayer player = (EntityPlayer)obj;
         if (PlayerEligibility.isRealPlayer(player)) {
            realPlayerIds.add(player.func_110124_au());
         }
         if (PlayerEligibility.shouldCheckPlayer(player) && !AlertManager.isMarked(player.func_110124_au())) {
            checkablePlayerIds.add(player.func_110124_au());
         }
      }
      ANTICHEAT.retainPlayers(checkablePlayerIds, realPlayerIds);
      for (Object obj : players) {
         if (!(obj instanceof EntityPlayer)) {
            continue;
         }
         EntityPlayer player = (EntityPlayer)obj;
         if (PlayerEligibility.shouldCheckPlayer(player) && !AlertManager.isMarked(player.func_110124_au())) {
            ANTICHEAT.anticheatCheck(player);
         }
      }
   }

   @SubscribeEvent
   public void onWorldUnload(WorldEvent.Unload event) {
      ANTICHEAT.clearAll();
   }
}
