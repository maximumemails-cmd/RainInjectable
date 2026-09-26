package first.rain.anticheat.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import first.rain.anticheat.RainCore;

/**
 * Defines the ClickGUI open key (default: Right Shift, rebindable in vanilla
 * Controls under the "Rain" category) and the master toggle key. Registration
 * is done by RainCore.start.
 */
public final class ClickGuiKeybind {
   public static final KeyBinding OPEN_GUI = new KeyBinding("Open Rain GUI", Keyboard.KEY_RSHIFT, "Rain");
   public static final KeyBinding TOGGLE_RAIN = new KeyBinding("Toggle Rain (master)", Keyboard.KEY_NONE, "Rain");

   @SubscribeEvent
   public void onKeyInput(InputEvent.KeyInputEvent event) {
      if (TOGGLE_RAIN.func_151468_f()) { // isPressed — consumes the press
         RainCore.toggle();
      }
      if (OPEN_GUI.func_151468_f()) { // isPressed — consumes the press
         Minecraft mc = Minecraft.func_71410_x();
         if (mc.field_71462_r == null) { // no screen already open
            mc.func_147108_a(new ClickGui()); // displayGuiScreen
         }
      }
   }
}
