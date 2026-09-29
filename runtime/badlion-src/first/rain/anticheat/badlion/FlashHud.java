package first.rain.anticheat.badlion;

import first.rain.anticheat.gui.ClickGui;
import first.rain.anticheat.util.anticheat.FlashEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.IChatComponent;

/** Decorates the existing HUD so the client's chat, titles and overlays survive. */
public final class FlashHud extends GuiIngame {
   private final GuiIngame delegate;
   private final Minecraft game;

   private FlashHud(Minecraft game, GuiIngame delegate) {
      super(game);
      this.game = game;
      this.delegate = delegate;
      this.field_73843_a = delegate.field_73843_a;
   }

   public static void install(Minecraft game) {
      if (game.field_71456_v != null && !(game.field_71456_v instanceof FlashHud)) {
         game.field_71456_v = new FlashHud(game, game.field_71456_v);
      }
   }

   @Override public void func_175180_a(float partialTicks) {
      delegate.field_73843_a = field_73843_a;
      delegate.func_175180_a(partialTicks);
      field_73843_a = delegate.field_73843_a;
      if (!(game.field_71462_r instanceof ClickGui)) FlashEffect.render();
   }
   @Override public void func_73831_a() {
      delegate.field_73843_a = field_73843_a;
      delegate.func_73831_a();
      field_73843_a = delegate.field_73843_a;
   }
   // GuiIngame's constructor invokes this virtual method before delegate is assigned.
   @Override public void func_175177_a() {
      if (delegate == null) super.func_175177_a();
      else delegate.func_175177_a();
   }
   @Override public void func_175186_a(ScaledResolution r, int x) { delegate.func_175186_a(r, x); }
   @Override public void func_175176_b(ScaledResolution r, int x) { delegate.func_175176_b(r, x); }
   @Override public void func_181551_a(ScaledResolution r) { delegate.func_181551_a(r); }
   @Override public void func_175185_b(ScaledResolution r) { delegate.func_175185_b(r); }
   @Override public void func_180478_c(ScaledResolution r) { delegate.func_180478_c(r); }
   @Override public void func_73833_a(String s) { delegate.func_73833_a(s); }
   @Override public void func_110326_a(String s, boolean b) { delegate.func_110326_a(s, b); }
   @Override public void func_175178_a(String a, String b, int c, int d, int e) {
      delegate.func_175178_a(a, b, c, d, e);
   }
   @Override public void func_175188_a(IChatComponent c, boolean b) { delegate.func_175188_a(c, b); }
   @Override public GuiNewChat func_146158_b() { return delegate.func_146158_b(); }
   @Override public int func_73834_c() { return delegate.func_73834_c(); }
   @Override public FontRenderer func_175179_f() { return delegate.func_175179_f(); }
   @Override public GuiSpectator func_175187_g() { return delegate.func_175187_g(); }
   @Override public GuiPlayerTabOverlay func_175181_h() { return delegate.func_175181_h(); }
   @Override public void func_181029_i() { delegate.func_181029_i(); }
}
