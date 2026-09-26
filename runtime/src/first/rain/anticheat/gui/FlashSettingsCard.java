package first.rain.anticheat.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.RenderUtil;
import first.rain.anticheat.util.anticheat.FlashNotification;

/**
 * The Notifications-tab card for the Screen Flash module: enable toggle,
 * Test button, color swatch row, and a draggable opacity slider. Writes
 * straight to cfg.v.flash* fields.
 */
public class FlashSettingsCard {
   private static final int PAD = 8;
   private static final int TOGGLE_WIDTH = 26;
   private static final int TOGGLE_HEIGHT = 12;
   private static final int SWATCH_SIZE = 12;
   private static final int SWATCH_GAP = 6;
   private static final int[] SWATCHES = {0xFF3B30, 0xFF8A00, 0xFFD60A, 0x32D74B, 0x0A84FF, 0xBF5AF2, 0xFFFFFF};

   private final int width;
   private final int height;
   private int x;
   private int y;
   private float knobAnim;
   private boolean draggingOpacity;

   public FlashSettingsCard(int width, int height) {
      this.width = width;
      this.height = height;
      this.knobAnim = cfg.v.flashEnabled ? 1.0F : 0.0F;
   }

   public void setPosition(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public void render(FontRenderer fontRenderer, int mouseX, int mouseY, float deltaSeconds, float alphaMult) {
      if (alphaMult <= 0.05F) {
         return;
      }
      boolean enabled = cfg.v.flashEnabled;
      boolean hovered = this.isMouseOver(mouseX, mouseY);

      float target = enabled ? 1.0F : 0.0F;
      this.knobAnim += (target - this.knobAnim) * Math.min(1.0F, deltaSeconds * 12.0F);
      if (Math.abs(target - this.knobAnim) < 0.01F) {
         this.knobAnim = target;
      }

      RenderUtil.drawRoundedRect(this.x, this.y, this.width, this.height, 5.0F,
         RenderUtil.applyAlpha(hovered ? 0xFF1D1D1D : 0xFF131313, alphaMult));
      RenderUtil.drawRoundedOutline(this.x, this.y, this.width, this.height, 5.0F, 1.0F,
         RenderUtil.applyAlpha(enabled ? 0xC8FFFFFF : 0x32FFFFFF, alphaMult));

      // header row: name, Test button, toggle
      fontRenderer.func_175063_a("Screen Flash", (float)(this.x + PAD), (float)(this.y + PAD), RenderUtil.applyAlpha(0xFFFFFFFF, alphaMult));
      boolean testHovered = this.isOverTest(mouseX, mouseY);
      RenderUtil.drawRoundedOutline(this.testX(), this.y + PAD - 1, 30.0F, 13.0F, 6.0F, 1.0F,
         RenderUtil.applyAlpha(testHovered ? 0xC8FFFFFF : 0x50FFFFFF, alphaMult));
      fontRenderer.func_78276_b("Test", this.testX() + (30 - fontRenderer.func_78256_a("Test")) / 2, this.y + PAD + 2,
         RenderUtil.applyAlpha(testHovered ? 0xFFFFFFFF : 0xFF9A9A9A, alphaMult));
      RenderUtil.drawTogglePill(this.toggleX(), this.y + PAD, TOGGLE_WIDTH, TOGGLE_HEIGHT, this.knobAnim, alphaMult);

      List<String> lines = fontRenderer.func_78271_c("Floods the screen + plays a sound whenever an alert fires.", this.width - PAD * 2);
      for (int i = 0; i < lines.size() && i < 2; ++i) {
         fontRenderer.func_78276_b(lines.get(i), this.x + PAD, this.y + 24 + i * 10, RenderUtil.applyAlpha(0xFF8C8C8C, alphaMult));
      }

      // color swatch row
      fontRenderer.func_78276_b("Color", this.x + PAD, this.y + 50, RenderUtil.applyAlpha(0xFF8C8C8C, alphaMult));
      for (int i = 0; i < SWATCHES.length; ++i) {
         int sx = this.swatchX(i);
         int sy = this.swatchY();
         boolean selected = (cfg.v.flashColor & 0xFFFFFF) == SWATCHES[i];
         RenderUtil.drawRoundedRect(sx, sy, SWATCH_SIZE, SWATCH_SIZE, 3.0F, RenderUtil.applyAlpha(0xFF000000 | SWATCHES[i], alphaMult));
         RenderUtil.drawRoundedOutline(sx, sy, SWATCH_SIZE, SWATCH_SIZE, 3.0F, 1.0F,
            RenderUtil.applyAlpha(selected ? 0xFFFFFFFF : 0x30FFFFFF, alphaMult));
      }

      // opacity slider
      int opacity = Math.max(0, Math.min(100, cfg.v.flashOpacity));
      fontRenderer.func_78276_b("Opacity", this.x + PAD, this.y + 70, RenderUtil.applyAlpha(0xFF8C8C8C, alphaMult));
      String pct = opacity + "%";
      fontRenderer.func_78276_b(pct, this.x + this.width - PAD - fontRenderer.func_78256_a(pct), this.y + 70,
         RenderUtil.applyAlpha(0xFFFFFFFF, alphaMult));
      int trackX = this.trackX();
      int trackY = this.trackY();
      int trackW = this.trackWidth();
      RenderUtil.drawRoundedRect(trackX, trackY, trackW, 6.0F, 3.0F, RenderUtil.applyAlpha(0xFF202020, alphaMult));
      float fill = (float)trackW * (float)opacity / 100.0F;
      if (fill > 2.0F) {
         RenderUtil.drawRoundedRect(trackX, trackY, fill, 6.0F, 3.0F, RenderUtil.applyAlpha(0xFF000000 | cfg.v.flashColor & 0xFFFFFF, alphaMult));
      }
      RenderUtil.drawCircle(trackX + fill, trackY + 3.0F, 5.0F, RenderUtil.applyAlpha(0xFFF2F2F2, alphaMult));
   }

   public boolean mouseClicked(int mouseX, int mouseY, int button) {
      if (button != 0 || !this.isMouseOver(mouseX, mouseY)) {
         return false;
      }
      if (this.isOverToggle(mouseX, mouseY)) {
         cfg.v.flashEnabled = !cfg.v.flashEnabled;
         this.playClick();
         return true;
      }
      if (this.isOverTest(mouseX, mouseY)) {
         FlashNotification.test();
         return true;
      }
      for (int i = 0; i < SWATCHES.length; ++i) {
         if (this.isOver(mouseX, mouseY, this.swatchX(i) - 1, this.swatchY() - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2)) {
            cfg.v.flashColor = SWATCHES[i];
            this.playClick();
            return true;
         }
      }
      if (this.isOver(mouseX, mouseY, this.trackX() - 4, this.trackY() - 5, this.trackWidth() + 8, 16)) {
         this.draggingOpacity = true;
         this.updateOpacity(mouseX);
         return true;
      }
      return true; // consume clicks on the card body so nothing behind reacts
   }

   public void mouseDragged(int mouseX, int mouseY) {
      if (this.draggingOpacity) {
         this.updateOpacity(mouseX);
      }
   }

   public void mouseReleased() {
      this.draggingOpacity = false;
   }

   private void updateOpacity(int mouseX) {
      float t = (float)(mouseX - this.trackX()) / (float)this.trackWidth();
      t = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
      cfg.v.flashOpacity = Math.round(t * 100.0F);
   }

   private void playClick() {
      Minecraft.func_71410_x().func_147118_V().func_147682_a(
         PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
   }

   private int toggleX() {
      return this.x + this.width - PAD - TOGGLE_WIDTH;
   }

   private int testX() {
      return this.toggleX() - 6 - 30;
   }

   private int swatchX(int index) {
      return this.x + PAD + 38 + index * (SWATCH_SIZE + SWATCH_GAP);
   }

   private int swatchY() {
      return this.y + 48;
   }

   private int trackX() {
      return this.x + PAD;
   }

   private int trackY() {
      return this.y + 84;
   }

   private int trackWidth() {
      return this.width - PAD * 2;
   }

   private boolean isOverToggle(int mouseX, int mouseY) {
      return this.isOver(mouseX, mouseY, this.toggleX() - 2, this.y + PAD - 2, TOGGLE_WIDTH + 4, TOGGLE_HEIGHT + 4);
   }

   private boolean isOverTest(int mouseX, int mouseY) {
      return this.isOver(mouseX, mouseY, this.testX(), this.y + PAD - 1, 30, 13);
   }

   private boolean isOver(int mouseX, int mouseY, int ox, int oy, int ow, int oh) {
      return mouseX >= ox && mouseX < ox + ow && mouseY >= oy && mouseY < oy + oh;
   }

   private boolean isMouseOver(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }
}
