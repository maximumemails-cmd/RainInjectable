package first.rain.anticheat.gui;

import first.rain.anticheat.Rain;
import first.rain.anticheat.RainCore;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.RenderUtil;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class ClickGui extends GuiScreen {
   private static final int CARD_W = 120;
   private static final int CARD_H = 72;
   private static final int GAP = 8;
   private static final int PADDING = 10;
   private static final int TITLE_BAR_HEIGHT = 20;
   private static final int TAB_BAR_HEIGHT = 18;
   private static final int CONTENT_HEIGHT = 152;
   private static final int FLASH_CARD_HEIGHT = 100;
   private static final int NAMETAG_CARD_HEIGHT = 104;
   private static final int TAB_GAP = 24;
   private static final int TAB_ALERTS = 0;
   private static final int TAB_NOTIFICATIONS = 1;
   private static final int TAB_NAMETAGS = 2;

   private static int panelX = Integer.MIN_VALUE;
   private static int panelY = Integer.MIN_VALUE;
   private static int activeTab = TAB_ALERTS;

   private final List<ModuleCard> alertCards = new ArrayList<ModuleCard>();
   private final FlashSettingsCard flashCard;
   private final NametagSettingsCard nametagCard;
   private final ModuleCard debugCard;
   private final int panelWidth;
   private final int panelHeight;
   private boolean dragging;
   private int dragOffsetX;
   private int dragOffsetY;
   private long lastFrameMillis;
   private float tabAnim;

   public ClickGui() {
      this.alertCards.add(new ModuleCard("Rain (master)", "Enable or disable all detection and alerts.",
         () -> RainCore.isEnabled(), (v) -> RainCore.setEnabled(v, true), CARD_W, CARD_H));
      this.alertCards.add(new ModuleCard("Block overlap", "Repeated swing and block-use overlap; review only.",
         () -> cfg.v.detectAutoBlock, (v) -> cfg.v.detectAutoBlock = v, CARD_W, CARD_H));
      this.alertCards.add(new ModuleCard("Bridge rhythm", "Regular crouches while bridging; review only.",
         () -> cfg.v.detectLegitScaffold, (v) -> cfg.v.detectLegitScaffold = v, CARD_W, CARD_H));
      this.alertCards.add(new ModuleCard("Aim pattern", "Hurt-correlated aim presentation; review only.",
         () -> cfg.v.detectKillaura, (v) -> cfg.v.detectKillaura = v, CARD_W, CARD_H));
      this.alertCards.add(new ModuleCard("Temporal Analysis", "Four seconds of bounded player history.",
         () -> cfg.v.temporalAnalysis, (v) -> cfg.v.temporalAnalysis = v, CARD_W, CARD_H));

      this.flashCard = new FlashSettingsCard(CARD_W * 2 + GAP, FLASH_CARD_HEIGHT);
      this.nametagCard = new NametagSettingsCard(CARD_W * 3 + GAP * 2, NAMETAG_CARD_HEIGHT);
      this.debugCard = new ModuleCard("Debug Messages", "Verbose check output in chat.",
         () -> cfg.v.debugMessages, (v) -> cfg.v.debugMessages = v, CARD_W, CARD_H);
      this.panelWidth = PADDING * 2 + 3 * CARD_W + 2 * GAP;
      this.panelHeight = TITLE_BAR_HEIGHT + TAB_BAR_HEIGHT + PADDING + CONTENT_HEIGHT + PADDING;
      this.tabAnim = activeTab;
   }

   @Override
   public void func_73866_w_() {
      if (panelX == Integer.MIN_VALUE) {
         panelX = (this.logicalWidth() - this.panelWidth) / 2;
         panelY = (this.logicalHeight() - this.panelHeight) / 2;
      }
      this.clampPanel();
      this.lastFrameMillis = System.currentTimeMillis();
   }

   @Override
   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      int screenMouseX = mouseX, screenMouseY = mouseY;
      mouseX = this.logicalMouseX(mouseX);
      mouseY = this.logicalMouseY(mouseY);
      long now = System.currentTimeMillis();
      float deltaSeconds = Math.min((float)(now - this.lastFrameMillis) / 1000.0F, 0.1F);
      this.lastFrameMillis = now;

      if (this.dragging) {
         panelX = mouseX - this.dragOffsetX;
         panelY = mouseY - this.dragOffsetY;
         this.clampPanel();
      }

      float tabTarget = activeTab;
      this.tabAnim += (tabTarget - this.tabAnim) * Math.min(1.0F, deltaSeconds * 10.0F);
      if (Math.abs(tabTarget - this.tabAnim) < 0.01F) {
         this.tabAnim = tabTarget;
      }

      Gui.func_73734_a(0, 0, this.field_146294_l, this.field_146295_m, 0x88000000);
      GL11.glPushMatrix();
      GL11.glScalef(this.uiScale(), this.uiScale(), 1.0F);
      RenderUtil.drawRoundedShadow(panelX, panelY, this.panelWidth, this.panelHeight, 7.0F);
      RenderUtil.drawRoundedRect(panelX, panelY, this.panelWidth, this.panelHeight, 7.0F, 0xFA0C0C0C);
      RenderUtil.drawRoundedOutline(panelX, panelY, this.panelWidth, this.panelHeight, 7.0F, 1.0F, 0x46FFFFFF);

      int titleTextY = panelY + (TITLE_BAR_HEIGHT - 8) / 2;
      this.field_146289_q.func_175063_a("Rain", (float)(panelX + PADDING), (float)titleTextY, 0xFFFFFFFF);
      int statusColor = RainCore.isEnabled() ? 0xFF32D74B : 0xFFFF3B30;
      this.field_146289_q.func_175063_a(RainCore.isEnabled() ? "ON" : "OFF",
         (float)(panelX + PADDING + this.field_146289_q.func_78256_a("Rain ")), (float)titleTextY, statusColor);
      String hint = "drag";
      this.field_146289_q.func_78276_b(hint, panelX + this.panelWidth - PADDING - this.field_146289_q.func_78256_a(hint), titleTextY, 0xFF5A5A5A);

      this.drawTabs(mouseX, mouseY);

      float alertsAlpha = activeTab == TAB_ALERTS ? 1.0F : 0.0F;
      float notifAlpha = activeTab == TAB_NOTIFICATIONS ? 1.0F : 0.0F;
      float nametagAlpha = activeTab == TAB_NAMETAGS ? 1.0F : 0.0F;
      int contentY = panelY + TITLE_BAR_HEIGHT + TAB_BAR_HEIGHT + PADDING;
      int alertsMouseX = activeTab == TAB_ALERTS ? mouseX : -9999;
      int notifMouseX = activeTab == TAB_NOTIFICATIONS ? mouseX : -9999;
      int nametagMouseX = activeTab == TAB_NAMETAGS ? mouseX : -9999;

      if (alertsAlpha > 0.02F) {
         for (int i = 0; i < this.alertCards.size(); ++i) {
            ModuleCard card = this.alertCards.get(i);
            card.setPosition(ClickGuiLayout.cardX(panelX, i, PADDING, CARD_W, GAP),
               ClickGuiLayout.cardY(contentY, i, CARD_H, GAP));
            card.render(this.field_146289_q, alertsMouseX, mouseY, deltaSeconds, alertsAlpha);
         }
      }
      if (notifAlpha > 0.02F) {
         this.flashCard.setPosition(panelX + PADDING, contentY);
         this.flashCard.render(this.field_146289_q, notifMouseX, mouseY, deltaSeconds, notifAlpha);
         int exportX = panelX + PADDING + 2 * (CARD_W + GAP);
         boolean exportHover = activeTab == TAB_NOTIFICATIONS && mouseX >= exportX
            && mouseX < exportX + CARD_W && mouseY >= contentY && mouseY < contentY + CARD_H;
         RenderUtil.drawRoundedRect(exportX, contentY, CARD_W, CARD_H, 6.0F,
            exportHover ? 0xFF292929 : 0xFF1D1D1D);
         this.field_146289_q.func_78276_b("Export evidence", exportX + 10, contentY + 14, 0xFFFFFFFF);
         this.field_146289_q.func_78276_b("Local JSONL only", exportX + 10, contentY + 32, 0xFFAAAAAA);
         this.debugCard.setPosition(exportX, contentY + CARD_H + GAP);
         this.debugCard.render(this.field_146289_q, notifMouseX, mouseY, deltaSeconds, notifAlpha);
      }
      if (nametagAlpha > 0.02F) {
         this.nametagCard.setPosition(panelX + PADDING, contentY);
         this.nametagCard.render(this.field_146289_q, nametagMouseX, mouseY, deltaSeconds, nametagAlpha);
      }

      GL11.glPopMatrix();
      super.func_73863_a(screenMouseX, screenMouseY, partialTicks);
   }

   private void drawTabs(int mouseX, int mouseY) {
      int[] tabs = this.tabLayout();
      int tabTextY = panelY + TITLE_BAR_HEIGHT + (TAB_BAR_HEIGHT - 8) / 2;
      boolean hoverAlerts = this.isOverTab(mouseX, mouseY, tabs[0], tabs[1]);
      boolean hoverNotif = this.isOverTab(mouseX, mouseY, tabs[2], tabs[3]);
      boolean hoverNametag = this.isOverTab(mouseX, mouseY, tabs[4], tabs[5]);

      int alertsColor = activeTab == TAB_ALERTS ? 0xFFFFFFFF : (hoverAlerts ? 0xFFB4B4B4 : 0xFF6E6E6E);
      int notifColor = activeTab == TAB_NOTIFICATIONS ? 0xFFFFFFFF : (hoverNotif ? 0xFFB4B4B4 : 0xFF6E6E6E);
      int nametagColor = activeTab == TAB_NAMETAGS ? 0xFFFFFFFF : (hoverNametag ? 0xFFB4B4B4 : 0xFF6E6E6E);
      this.field_146289_q.func_78276_b("Alerts", tabs[0], tabTextY, alertsColor);
      this.field_146289_q.func_78276_b("Notifications", tabs[2], tabTextY, notifColor);
      this.field_146289_q.func_78276_b("Nametags", tabs[4], tabTextY, nametagColor);

      int lineY = panelY + TITLE_BAR_HEIGHT + TAB_BAR_HEIGHT - 2;
      Gui.func_73734_a(panelX + PADDING, lineY, panelX + this.panelWidth - PADDING, lineY + 1, 0x1EFFFFFF);
      int fromTab = Math.max(0, Math.min(TAB_NAMETAGS, (int)Math.floor(this.tabAnim)));
      int toTab = Math.max(0, Math.min(TAB_NAMETAGS, fromTab + 1));
      float t = this.tabAnim - (float)fromTab;
      float indicatorX = this.tabX(tabs, fromTab) + (this.tabX(tabs, toTab) - this.tabX(tabs, fromTab)) * t;
      float indicatorW = this.tabW(tabs, fromTab) + (this.tabW(tabs, toTab) - this.tabW(tabs, fromTab)) * t;
      RenderUtil.drawRoundedRect(indicatorX - 2.0F, (float)(lineY - 1), indicatorW + 4.0F, 2.0F, 1.0F, 0xFFFFFFFF);
   }

   private int[] tabLayout() {
      int alertsW = this.field_146289_q.func_78256_a("Alerts");
      int notifW = this.field_146289_q.func_78256_a("Notifications");
      int nametagW = this.field_146289_q.func_78256_a("Nametags");
      int totalW = alertsW + notifW + nametagW + TAB_GAP * 2;
      int baseX = panelX + (this.panelWidth - totalW) / 2;
      int notifX = baseX + alertsW + TAB_GAP;
      int nametagX = notifX + notifW + TAB_GAP;
      return new int[]{baseX, alertsW, notifX, notifW, nametagX, nametagW};
   }

   private int tabX(int[] tabs, int tab) {
      return tabs[tab * 2];
   }

   private int tabW(int[] tabs, int tab) {
      return tabs[tab * 2 + 1];
   }

   private boolean isOverTab(int mouseX, int mouseY, int tabX, int tabW) {
      return mouseY >= panelY + TITLE_BAR_HEIGHT && mouseY < panelY + TITLE_BAR_HEIGHT + TAB_BAR_HEIGHT
         && mouseX >= tabX - 6 && mouseX < tabX + tabW + 6;
   }

   private void setTab(int tab) {
      if (activeTab != tab) {
         activeTab = tab;
         this.field_146297_k.func_147118_V().func_147682_a(
            PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
      }
   }

   @Override
   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) {
      super.func_73864_a(mouseX, mouseY, mouseButton);
      mouseX = this.logicalMouseX(mouseX);
      mouseY = this.logicalMouseY(mouseY);
      if (mouseButton != 0) {
         return;
      }
      if (mouseX >= panelX && mouseX < panelX + this.panelWidth && mouseY >= panelY && mouseY < panelY + TITLE_BAR_HEIGHT) {
         this.dragging = true;
         this.dragOffsetX = mouseX - panelX;
         this.dragOffsetY = mouseY - panelY;
         return;
      }

      int[] tabs = this.tabLayout();
      if (this.isOverTab(mouseX, mouseY, tabs[0], tabs[1])) {
         this.setTab(TAB_ALERTS);
         return;
      }
      if (this.isOverTab(mouseX, mouseY, tabs[2], tabs[3])) {
         this.setTab(TAB_NOTIFICATIONS);
         return;
      }
      if (this.isOverTab(mouseX, mouseY, tabs[4], tabs[5])) {
         this.setTab(TAB_NAMETAGS);
         return;
      }

      if (activeTab == TAB_ALERTS) {
         for (ModuleCard card : this.alertCards) {
            if (card.mouseClicked(mouseX, mouseY, mouseButton)) {
               return;
            }
         }
      } else if (activeTab == TAB_NOTIFICATIONS) {
         if (this.flashCard.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
         }
         int exportX = panelX + PADDING + 2 * (CARD_W + GAP);
         int contentY = panelY + TITLE_BAR_HEIGHT + TAB_BAR_HEIGHT + PADDING;
         if (mouseX >= exportX && mouseX < exportX + CARD_W
            && mouseY >= contentY && mouseY < contentY + CARD_H) {
            File output = new File(new File(this.field_146297_k.field_71412_D, "config"),
               "rain-evidence-" + System.currentTimeMillis() + ".jsonl");
            try {
               Rain.ANTICHEAT.exportEvidence(output);
               Rain.addMessage("Rain evidence saved locally: " + output.getAbsolutePath());
            } catch (IOException e) {
               Rain.addMessage("Rain evidence export failed: " + e.getMessage());
            }
            return;
         }
         this.debugCard.mouseClicked(mouseX, mouseY, mouseButton);
      } else if (activeTab == TAB_NAMETAGS) {
         this.nametagCard.mouseClicked(mouseX, mouseY, mouseButton);
      }
   }

   @Override
   protected void func_146273_a(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.func_146273_a(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      mouseX = this.logicalMouseX(mouseX);
      mouseY = this.logicalMouseY(mouseY);
      if (activeTab == TAB_NOTIFICATIONS) {
         this.flashCard.mouseDragged(mouseX, mouseY);
      } else if (activeTab == TAB_NAMETAGS) {
         this.nametagCard.mouseDragged(mouseX, mouseY);
      }
   }

   @Override
   protected void func_146286_b(int mouseX, int mouseY, int state) {
      this.dragging = false;
      this.flashCard.mouseReleased();
      this.nametagCard.mouseReleased();
      super.func_146286_b(mouseX, mouseY, state);
   }

   @Override
   protected void func_73869_a(char typedChar, int keyCode) {
      if (keyCode != 0 && keyCode == ClickGuiKeybind.OPEN_GUI.func_151463_i()) {
         this.field_146297_k.func_147108_a((GuiScreen)null);
         return;
      }
      super.func_73869_a(typedChar, keyCode);
   }

   @Override
   public boolean func_73868_f() {
      return false;
   }

   @Override
   public void func_146281_b() {
      this.dragging = false;
      this.flashCard.mouseReleased();
      this.nametagCard.mouseReleased();
      RainCore.saveKeybinds();
   }

   private void clampPanel() {
      panelX = Math.max(0, Math.min(panelX, this.logicalWidth() - this.panelWidth));
      panelY = Math.max(0, Math.min(panelY, this.logicalHeight() - this.panelHeight));
   }

   private float uiScale() {
      return ClickGuiLayout.scale(this.field_146294_l, this.field_146295_m,
         this.panelWidth, this.panelHeight);
   }
   private int logicalWidth() { return ClickGuiLayout.logicalExtent(this.field_146294_l, this.uiScale()); }
   private int logicalHeight() { return ClickGuiLayout.logicalExtent(this.field_146295_m, this.uiScale()); }
   private int logicalMouseX(int x) { return (int)(x / this.uiScale()); }
   private int logicalMouseY(int y) { return (int)(y / this.uiScale()); }
}
