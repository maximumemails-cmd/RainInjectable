import first.rain.anticheat.badlion.FlashHud;
import first.rain.anticheat.gui.ClickGui;
import first.rain.anticheat.util.anticheat.FlashEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import java.lang.reflect.Method;

public class HudHarness {
   private static int assertions;
   private static void check(boolean value, String message) {
      assertions++;
      if (!value) throw new AssertionError(message);
   }
   public static void main(String[] args) throws Exception {
      Minecraft game = new Minecraft();
      FlashHud.install(game);
      check(game.field_71456_v == null, "absent HUD left alone");
      GuiIngame original = new GuiIngame(game);
      original.field_73843_a = 0.4F;
      game.field_71456_v = original;
      FlashHud.install(game); // also verifies the constructor's virtual reset call
      GuiIngame hud = game.field_71456_v;
      check(hud instanceof FlashHud && hud.field_73843_a == 0.4F, "existing state retained");
      FlashHud.install(game);
      check(game.field_71456_v == hud, "installation is idempotent");
      for (Method method : GuiIngame.class.getDeclaredMethods()) {
         if (!method.getName().startsWith("func_")) continue;
         Class<?>[] types = method.getParameterTypes();
         Object[] values = new Object[types.length];
         for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) values[i] = 2;
            else if (types[i] == float.class) values[i] = 0.5F;
            else if (types[i] == boolean.class) values[i] = true;
            else if (types[i] == String.class) values[i] = "test";
         }
         int calls = original.calls;
         method.invoke(hud, values);
         check(original.calls == calls + 1 && method.getName().equals(original.last),
            "delegates original HUD API: " + method.getName());
      }
      check(hud.func_146158_b() == original.chat && hud.func_175181_h() == original.tab
         && hud.func_175179_f() == original.font && hud.func_175187_g() == original.spectator,
         "retains client chat, tab list, spectator and font instances");
      hud.field_73843_a = 0.2F;
      hud.func_175180_a(0.5F);
      check(hud.field_73843_a == original.field_73843_a && Math.abs(hud.field_73843_a - 0.7F) < 0.001F,
         "render synchronizes mutable vignette state");
      int renders = FlashEffect.renders;
      hud.func_175180_a(0);
      check(FlashEffect.renders == renders + 1, "HUD renders flash during gameplay");
      game.field_71462_r = new ClickGui();
      hud.func_175180_a(0);
      check(FlashEffect.renders == renders + 1, "settings render their own flash once");
      game.field_71456_v = new GuiIngame(game);
      FlashHud.install(game);
      check(game.field_71456_v instanceof FlashHud && game.field_71456_v != hud, "client HUD replacement reattaches");
      System.out.println("FLASH HUD HARNESS OK: " + assertions + " assertions");
   }
}
