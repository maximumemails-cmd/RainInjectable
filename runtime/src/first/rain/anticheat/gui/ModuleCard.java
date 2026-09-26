package first.rain.anticheat.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import first.rain.anticheat.util.RenderUtil;

/**
 * One grid cell of the ClickGUI: name, short description, and an animated
 * rounded toggle. Holds no detection logic — the getter/setter lambdas read
 * and write the matching cfg.v boolean. Renders with a page-fade multiplier
 * so tab transitions can crossfade whole pages.
 */
public class ModuleCard {
   private static final int PAD = 8;
   private static final int TOGGLE_WIDTH = 26;
   private static final int TOGGLE_HEIGHT = 12;

   private static final int COLOR_CARD = 0xFF131313;
   private static final int COLOR_CARD_HOVER = 0xFF1D1D1D;
   private static final int COLOR_BORDER_ON = 0xC8FFFFFF;
   private static final int COLOR_BORDER_OFF = 0x32FFFFFF;
   private static final int COLOR_DESC = 0xFF8C8C8C;

   private final String name;
   private final String description;
   private final BooleanSupplier getter;
   private final Consumer<Boolean> setter;
   private final int width;
   private final int height;

   private int x;
   private int y;
   private float knobAnim; // 0 = fully off, 1 = fully on

   public ModuleCard(String name, String description, BooleanSupplier getter, Consumer<Boolean> setter, int width, int height) {
      this.name = name;
      this.description = description;
      this.getter = getter;
      this.setter = setter;
      this.width = width;
      this.height = height;
      this.knobAnim = getter.getAsBoolean() ? 1.0F : 0.0F;
   }

   public void setPosition(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public void render(FontRenderer fontRenderer, int mouseX, int mouseY, float deltaSeconds, float alphaMult) {
      if (alphaMult <= 0.05F) {
         return; // fully faded out — also keeps FontRenderer from treating ~0 alpha as opaque
      }
      boolean enabled = this.getter.getAsBoolean();
      boolean hovered = this.isMouseOver(mouseX, mouseY);

      float target = enabled ? 1.0F : 0.0F;
      this.knobAnim += (target - this.knobAnim) * Math.min(1.0F, deltaSeconds * 12.0F);
      if (Math.abs(target - this.knobAnim) < 0.01F) {
         this.knobAnim = target;
      }

      RenderUtil.drawRoundedRect(this.x, this.y, this.width, this.height, 5.0F,
         RenderUtil.applyAlpha(hovered ? COLOR_CARD_HOVER : COLOR_CARD, alphaMult));
      RenderUtil.drawRoundedOutline(this.x, this.y, this.width, this.height, 5.0F, 1.0F,
         RenderUtil.applyAlpha(enabled ? COLOR_BORDER_ON : COLOR_BORDER_OFF, alphaMult));

      fontRenderer.func_175063_a(this.name, (float)(this.x + PAD), (float)(this.y + PAD), RenderUtil.applyAlpha(0xFFFFFFFF, alphaMult)); // drawStringWithShadow
      List<String> lines = fontRenderer.func_78271_c(this.description, this.width - PAD * 2);                                            // listFormattedStringToWidth
      for (int i = 0; i < lines.size() && i < 2; ++i) {
         fontRenderer.func_78276_b(lines.get(i), this.x + PAD, this.y + 20 + i * 10, RenderUtil.applyAlpha(COLOR_DESC, alphaMult));      // drawString
      }

      int toggleX = this.x + this.width - PAD - TOGGLE_WIDTH;
      int toggleY = this.y + this.height - PAD - TOGGLE_HEIGHT;
      fontRenderer.func_78276_b(enabled ? "ON" : "OFF", this.x + PAD, toggleY + 2,
         RenderUtil.applyAlpha(enabled ? 0xFFFFFFFF : 0xFF6E6E6E, alphaMult));
      RenderUtil.drawTogglePill(toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT, this.knobAnim, alphaMult);
   }

   /**
    * @return true if the click was consumed (anywhere on the card toggles).
    */
   public boolean mouseClicked(int mouseX, int mouseY, int button) {
      if (button != 0 || !this.isMouseOver(mouseX, mouseY)) {
         return false;
      }
      this.setter.accept(!this.getter.getAsBoolean());
      Minecraft.func_71410_x().func_147118_V().func_147682_a( // getSoundHandler().playSound(...)
         PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
      return true;
   }

   private boolean isMouseOver(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }
}
