package first.rain.anticheat.util.anticheat;

import first.rain.anticheat.gui.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Forge HUD hook. ClickGui draws the effect above its own opaque panel. */
public class FlashNotification {
   public static void trigger() { FlashEffect.trigger(); }
   public static void test() { FlashEffect.test(); }

   @SubscribeEvent
   public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
      if (event.type == RenderGameOverlayEvent.ElementType.ALL
         && !(Minecraft.func_71410_x().field_71462_r instanceof ClickGui)) FlashEffect.render();
   }
}
