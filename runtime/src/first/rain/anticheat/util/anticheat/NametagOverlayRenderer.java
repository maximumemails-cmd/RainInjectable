package first.rain.anticheat.util.anticheat;

import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.RenderUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NametagOverlayRenderer {
   private static final int MAX_TAB_PLAYERS = 80;

   @SubscribeEvent
   public void onRenderPlayerList(RenderGameOverlayEvent.Post event) {
      if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST || !cfg.v.nametagEnabled) {
         return;
      }
      if (cfg.v.nametagOpacity <= 0) {
         return;
      }
      if (!AlertManager.hasMarkedPlayers()) {
         return;
      }

      Minecraft mc = Minecraft.func_71410_x();
      if (mc.field_71441_e == null || mc.field_71439_g == null || mc.func_147114_u() == null) {
         return;
      }
      if (!mc.field_71474_y.field_74321_H.func_151470_d()) {
         return;
      }

      NetHandlerPlayClient netHandler = mc.func_147114_u();
      Set<NetworkPlayerInfo> markedInfos = Collections.newSetFromMap(new IdentityHashMap<NetworkPlayerInfo, Boolean>());
      for (UUID uuid : AlertManager.markedPlayerIds()) {
         NetworkPlayerInfo info = netHandler.func_175102_a(uuid);
         if (info != null) {
            markedInfos.add(info);
         }
      }
      if (markedInfos.isEmpty()) {
         return;
      }

      GuiPlayerTabOverlay tabOverlay = mc.field_71456_v.func_175181_h();
      List<NetworkPlayerInfo> players = this.sortedPlayers(mc, tabOverlay);
      if (players.size() > MAX_TAB_PLAYERS) {
         players = new ArrayList<NetworkPlayerInfo>(players.subList(0, MAX_TAB_PLAYERS));
      }

      int markedVisible = 0;
      for (NetworkPlayerInfo info : players) {
         if (markedInfos.contains(info)) {
            ++markedVisible;
         }
      }
      if (markedVisible == 0) {
         return;
      }

      FontRenderer font = mc.field_71466_p;
      int maxNameWidth = 0;
      for (NetworkPlayerInfo info : players) {
         maxNameWidth = Math.max(maxNameWidth, font.func_78256_a(tabOverlay.func_175243_a(info)));
      }

      Scoreboard scoreboard = mc.field_71441_e.func_96441_U();
      ScoreObjective objective = scoreboard.func_96539_a(0);
      int scoreWidth = objective != null && objective.func_178766_e() == IScoreObjectiveCriteria.EnumRenderType.HEARTS ? 90 : 0;

      int playerCount = players.size();
      int rows = playerCount;
      int columns = 1;
      while (rows > 20) {
         ++columns;
         rows = (playerCount + columns - 1) / columns;
      }

      boolean hasSkins = true;
      int screenWidth = event.resolution.func_78326_a();
      int rowWidth = Math.min(columns * ((hasSkins ? 9 : 0) + maxNameWidth + scoreWidth + 13), screenWidth - 50) / columns;
      int startX = screenWidth / 2 - (rowWidth * columns + (columns - 1) * 5) / 2;
      int baseY = this.playerListTop(font, tabOverlay, screenWidth);
      int iconColor = this.iconColor();

      for (int i = 0; i < playerCount; ++i) {
         NetworkPlayerInfo info = players.get(i);
         if (!markedInfos.contains(info)) {
            continue;
         }
         int column = i / rows;
         int row = i % rows;
         int rowX = startX + column * rowWidth + column * 5;
         int rowY = baseY + row * 9;
         String name = tabOverlay.func_175243_a(info);
         int nameX = rowX + (hasSkins ? 9 : 0);
         float markerX = Math.min(nameX + font.func_78256_a(name) + 7.0F, rowX + rowWidth - 4.0F);
         float markerY = rowY + 4.5F;
         RenderUtil.drawTriangle(markerX, markerY, 5.5F, 6.0F, iconColor);
         RenderUtil.drawTriangleOutline(markerX, markerY, 5.5F, 6.0F, 1.0F, 0xC8000000);
      }
   }

   private List<NetworkPlayerInfo> sortedPlayers(Minecraft mc, GuiPlayerTabOverlay tabOverlay) {
      List<NetworkPlayerInfo> vanillaSorted = this.vanillaSortedPlayers(mc);
      if (vanillaSorted != null) {
         return vanillaSorted;
      }

      List<NetworkPlayerInfo> players = new ArrayList<NetworkPlayerInfo>(mc.func_147114_u().func_175106_d());
      Collections.sort(players, new Comparator<NetworkPlayerInfo>() {
         @Override
         public int compare(NetworkPlayerInfo a, NetworkPlayerInfo b) {
            boolean aActive = a.func_178848_b() != GameType.SPECTATOR;
            boolean bActive = b.func_178848_b() != GameType.SPECTATOR;
            if (aActive != bActive) {
               return aActive ? -1 : 1;
            }

            int team = teamName(a).compareTo(teamName(b));
            if (team != 0) {
               return team;
            }

            return cleanName(tabOverlay.func_175243_a(a)).compareTo(cleanName(tabOverlay.func_175243_a(b)));
         }
      });
      return players;
   }

   private List<NetworkPlayerInfo> vanillaSortedPlayers(Minecraft mc) {
      try {
         Field orderingField = GuiPlayerTabOverlay.class.getDeclaredField("field_175252_a");
         orderingField.setAccessible(true);
         Object ordering = orderingField.get(null);
         Method sortedCopy = ordering.getClass().getMethod("sortedCopy", Iterable.class);
         Object result = sortedCopy.invoke(ordering, mc.func_147114_u().func_175106_d());
         if (!(result instanceof List)) {
            return null;
         }

         List<?> rawPlayers = (List<?>)result;
         List<NetworkPlayerInfo> players = new ArrayList<NetworkPlayerInfo>(rawPlayers.size());
         for (Object rawPlayer : rawPlayers) {
            if (rawPlayer instanceof NetworkPlayerInfo) {
               players.add((NetworkPlayerInfo)rawPlayer);
            }
         }
         return players;
      } catch (Exception ignored) {
         return null;
      }
   }

   private int playerListTop(FontRenderer font, GuiPlayerTabOverlay tabOverlay, int screenWidth) {
      IChatComponent header = this.readTabComponent(tabOverlay, "field_175256_i");
      if (header == null) {
         return 10;
      }

      List<String> lines = font.func_78271_c(header.func_150254_d(), screenWidth - 50);
      return 10 + lines.size() * font.field_78288_b + 1;
   }

   private IChatComponent readTabComponent(GuiPlayerTabOverlay tabOverlay, String fieldName) {
      try {
         Field field = GuiPlayerTabOverlay.class.getDeclaredField(fieldName);
         field.setAccessible(true);
         Object value = field.get(tabOverlay);
         return value instanceof IChatComponent ? (IChatComponent)value : null;
      } catch (Exception ignored) {
         return null;
      }
   }

   private static String teamName(NetworkPlayerInfo info) {
      ScorePlayerTeam team = info.func_178850_i();
      return team == null ? "" : team.func_96661_b();
   }

   private static String cleanName(String name) {
      String clean = EnumChatFormatting.func_110646_a(name);
      return clean == null ? "" : clean;
   }

   private int iconColor() {
      int opacity = Math.max(0, Math.min(100, cfg.v.nametagOpacity));
      int alpha = opacity * 255 / 100;
      return alpha << 24 | cfg.v.nametagColor & 0xFFFFFF;
   }
}
