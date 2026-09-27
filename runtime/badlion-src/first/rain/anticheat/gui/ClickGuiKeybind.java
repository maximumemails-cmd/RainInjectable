package first.rain.anticheat.gui;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Badlion uses the game-thread keyboard poll in BadlionBootstrap. */
public final class ClickGuiKeybind {
   public static final KeyBinding OPEN_GUI = new KeyBinding("Open Rain GUI", Keyboard.KEY_RSHIFT, "Rain");
   public static final KeyBinding TOGGLE_RAIN = new KeyBinding("Toggle Rain (master)", Keyboard.KEY_NONE, "Rain");

   private ClickGuiKeybind() {}
}
