package first.rain.anticheat.util.anticheat;

import first.rain.anticheat.RainCore;
import first.rain.anticheat.config.cfg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Render-thread-only flash, with no dependency on a particular event bus. */
public final class FlashEffect {
   private static final FlashPulse pulse = new FlashPulse();
   private FlashEffect() {}

   public static void trigger() {
      if (RainCore.isEnabled() && cfg.v.flashEnabled) start(false);
   }
   public static void test() { start(true); }
   public static void clear() { pulse.clear(); }
   private static void start(boolean preview) {
      pulse.start(System.nanoTime() / 1000000L, preview);
      Minecraft.func_71410_x().func_147118_V().func_147682_a(
         PositionedSoundRecord.func_147674_a(new ResourceLocation("note.pling"), 0.5F));
   }
   public static void render() {
      int color = pulse.color(System.nanoTime() / 1000000L,
         RainCore.isEnabled() && cfg.v.flashEnabled, cfg.v.flashOpacity, cfg.v.flashColor);
      if ((color >>> 24) == 0) return;
      ScaledResolution res = new ScaledResolution(Minecraft.func_71410_x());
      boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      GL11.glDisable(GL11.GL_DEPTH_TEST);
      try {
         Gui.func_73734_a(0, 0, res.func_78326_a(), res.func_78328_b(), color);
      } finally {
         if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
      }
   }
}
