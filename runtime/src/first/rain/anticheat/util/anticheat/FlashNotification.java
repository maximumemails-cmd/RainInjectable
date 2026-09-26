package first.rain.anticheat.util.anticheat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import first.rain.anticheat.RainCore;
import first.rain.anticheat.config.cfg;

/**
 * Full-screen flash + sound that fires alongside chat alerts. An instance must
 * be registered on the Forge event bus (done in Rain.init). The flash is a
 * two-beat pulse that fades out over ~1.2s, tinted with the configured color
 * and capped at the configured opacity.
 */
public class FlashNotification {
   private static final long FLASH_DURATION_MS = 1200L;
   private static long flashStartMillis = -1L;

   /** Called by AlertManager whenever a chat alert is actually emitted. */
   public static void trigger() {
      if (cfg.v.flashEnabled) {
         start();
      }
   }

   /** GUI preview button — fires regardless of the enabled toggle. */
   public static void test() {
      start();
   }

   private static void start() {
      flashStartMillis = System.currentTimeMillis();
      Minecraft mc = Minecraft.func_71410_x();
      mc.func_147118_V().func_147682_a( // getSoundHandler().playSound(...)
         PositionedSoundRecord.func_147674_a(new ResourceLocation("note.pling"), 0.5F));
   }

   @SubscribeEvent
   public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
      if (!RainCore.isEnabled()) {
         return;
      }
      if (event.type != RenderGameOverlayEvent.ElementType.ALL || flashStartMillis < 0L) {
         return;
      }
      long elapsed = System.currentTimeMillis() - flashStartMillis;
      if (elapsed >= FLASH_DURATION_MS) {
         flashStartMillis = -1L;
         return;
      }

      float progress = (float)elapsed / (float)FLASH_DURATION_MS;
      float pulse = 0.55F + 0.45F * (float)Math.cos((double)progress * Math.PI * 4.0D); // two beats
      float envelope = 1.0F - progress;                                                 // overall fade-out
      int opacity = Math.max(0, Math.min(100, cfg.v.flashOpacity));
      float alpha = (float)opacity / 100.0F * pulse * envelope;
      if (alpha <= 0.01F) {
         return;
      }

      ScaledResolution res = event.resolution;
      int color = (int)(alpha * 255.0F) << 24 | cfg.v.flashColor & 0xFFFFFF;
      Gui.func_73734_a(0, 0, res.func_78326_a(), res.func_78328_b(), color); // drawRect over the whole screen
   }
}
