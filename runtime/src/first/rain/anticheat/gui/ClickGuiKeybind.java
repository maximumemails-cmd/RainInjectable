package first.rain.anticheat.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * Registers the ClickGUI open key (default: Right Shift, rebindable in
 * vanilla Controls under the "Rain" category) and opens the GUI on press.
 * Call ClickGuiKeybind.register() once from your mod's init.
 */
public final class ClickGuiKeybind {
   public static final KeyBinding OPEN_GUI = new KeyBinding("Open AntiCheat GUI", Keyboard.KEY_RSHIFT, "Rain");

   public static void register() {
      ClientRegistry.registerKeyBinding(OPEN_GUI);
      MinecraftForge.EVENT_BUS.register(new ClickGuiKeybind());
   }

   @SubscribeEvent
   public void onKeyInput(InputEvent.KeyInputEvent event) {
      if (OPEN_GUI.func_151468_f()) { // isPressed — consumes the press
         Minecraft mc = Minecraft.func_71410_x();
         if (mc.field_71462_r == null) { // no screen already open
            mc.func_147108_a(new ClickGui()); // displayGuiScreen
         }
      }
   }
}
