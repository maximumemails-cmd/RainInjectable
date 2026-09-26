package first.rain.anticheat.gui;

import first.rain.anticheat.config.cfg;
import first.rain.anticheat.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

public class NametagSettingsCard {
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

   public NametagSettingsCard(int width, int height) {
      this.width = width;
      this.height = height;
      this.knobAnim = cfg.v.nametagEnabled ? 1.0F : 0.0F;
   }

   public void setPosition(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public void render(FontRenderer fontRenderer, int mouseX, int mouseY, float deltaSeconds, float alphaMult) {
      if (alphaMult <= 0.05F) {
         return;
      }
      boolean enabled = cfg.v.nametagEnabled;
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

      fontRenderer.func_175063_a("Marked Icon", (float)(this.x + PAD), (float)(this.y + PAD), RenderUtil.applyAlpha(0xFFFFFFFF, alphaMult));
      RenderUtil.drawTogglePill(this.toggleX(), this.y + PAD, TOGGLE_WIDTH, TOGGLE_HEIGHT, this.knobAnim, alphaMult);

      int previewX = this.x + PAD;
      int previewY = this.y + 28;
      RenderUtil.drawRoundedRect(previewX, previewY, this.width - PAD * 2, 16.0F, 4.0F, RenderUtil.applyAlpha(0xFF202020, alphaMult));
      String previewName = "FlaggedPlayer";
      int previewNameX = previewX + 10;
      fontRenderer.func_78276_b(previewName, previewNameX, previewY + 4, RenderUtil.applyAlpha(0xFFE8E8E8, alphaMult));
      int color = RenderUtil.applyAlpha(0xFF000000 | cfg.v.nametagColor & 0xFFFFFF,
         alphaMult * (float)Math.max(0, Math.min(100, cfg.v.nametagOpacity)) / 100.0F);
      float markerX = previewNameX + fontRenderer.func_78256_a(previewName) + 9.0F;
      float markerY = previewY + 8.0F;
      RenderUtil.drawTriangle(markerX, markerY, 6.0F, 6.5F, color);
      RenderUtil.drawTriangleOutline(markerX, markerY, 6.0F, 6.5F, 1.0F,
         RenderUtil.applyAlpha(0xFFFFFFFF, alphaMult * 0.55F));

      fontRenderer.func_78276_b("Color", this.x + PAD, this.y + 54, RenderUtil.applyAlpha(0xFF8C8C8C, alphaMult));
      for (int i = 0; i < SWATCHES.length; ++i) {
         int sx = this.swatchX(i);
         int sy = this.swatchY();
         boolean selected = (cfg.v.nametagColor & 0xFFFFFF) == SWATCHES[i];
         RenderUtil.drawRoundedRect(sx, sy, SWATCH_SIZE, SWATCH_SIZE, 3.0F, RenderUtil.applyAlpha(0xFF000000 | SWATCHES[i], alphaMult));
         RenderUtil.drawRoundedOutline(sx, sy, SWATCH_SIZE, SWATCH_SIZE, 3.0F, 1.0F,
            RenderUtil.applyAlpha(selected ? 0xFFFFFFFF : 0x30FFFFFF, alphaMult));
      }

      int opacity = Math.max(0, Math.min(100, cfg.v.nametagOpacity));
      fontRenderer.func_78276_b("Opacity", this.x + PAD, this.y + 74, RenderUtil.applyAlpha(0xFF8C8C8C, alphaMult));
      String pct = opacity + "%";
      fontRenderer.func_78276_b(pct, this.x + this.width - PAD - fontRenderer.func_78256_a(pct), this.y + 74,
         RenderUtil.applyAlpha(0xFFFFFFFF, alphaMult));
      int trackX = this.trackX();
      int trackY = this.trackY();
      int trackW = this.trackWidth();
      RenderUtil.drawRoundedRect(trackX, trackY, trackW, 6.0F, 3.0F, RenderUtil.applyAlpha(0xFF202020, alphaMult));
      float fill = (float)trackW * (float)opacity / 100.0F;
      if (fill > 2.0F) {
         RenderUtil.drawRoundedRect(trackX, trackY, fill, 6.0F, 3.0F, RenderUtil.applyAlpha(0xFF000000 | cfg.v.nametagColor & 0xFFFFFF, alphaMult));
      }
      RenderUtil.drawCircle(trackX + fill, trackY + 3.0F, 5.0F, RenderUtil.applyAlpha(0xFFF2F2F2, alphaMult));
   }

   public boolean mouseClicked(int mouseX, int mouseY, int button) {
      if (button != 0 || !this.isMouseOver(mouseX, mouseY)) {
         return false;
      }
      if (this.isOverToggle(mouseX, mouseY)) {
         cfg.v.nametagEnabled = !cfg.v.nametagEnabled;
         this.playClick();
         return true;
      }
      for (int i = 0; i < SWATCHES.length; ++i) {
         if (this.isOver(mouseX, mouseY, this.swatchX(i) - 1, this.swatchY() - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2)) {
            cfg.v.nametagColor = SWATCHES[i];
            this.playClick();
            return true;
         }
      }
      if (this.isOver(mouseX, mouseY, this.trackX() - 4, this.trackY() - 5, this.trackWidth() + 8, 16)) {
         this.draggingOpacity = true;
         this.updateOpacity(mouseX);
         return true;
      }
      return true;
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
      cfg.v.nametagOpacity = Math.round(t * 100.0F);
   }

   private void playClick() {
      Minecraft.func_71410_x().func_147118_V().func_147682_a(
         PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
   }

   private int toggleX() {
      return this.x + this.width - PAD - TOGGLE_WIDTH;
   }

   private int swatchX(int index) {
      return this.x + PAD + 38 + index * (SWATCH_SIZE + SWATCH_GAP);
   }

   private int swatchY() {
      return this.y + 52;
   }

   private int trackX() {
      return this.x + PAD;
   }

   private int trackY() {
      return this.y + 88;
   }

   private int trackWidth() {
      return this.width - PAD * 2;
   }

   private boolean isOverToggle(int mouseX, int mouseY) {
      return this.isOver(mouseX, mouseY, this.toggleX() - 2, this.y + PAD - 2, TOGGLE_WIDTH + 4, TOGGLE_HEIGHT + 4);
   }

   private boolean isOver(int mouseX, int mouseY, int ox, int oy, int ow, int oh) {
      return mouseX >= ox && mouseX < ox + ow && mouseY >= oy && mouseY < oy + oh;
   }

   private boolean isMouseOver(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }
}
