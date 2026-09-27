import first.rain.anticheat.gui.ClickGuiLayout;

public final class GuiLayoutHarness {
   private static int assertions;
   private static void check(boolean condition, String message) {
      assertions++;
      if (!condition) throw new AssertionError(message);
   }
   private static void screen(int width, int height) {
      final int panelW = 396, panelH = 210, cardW = 120, cardH = 72, gap = 8, pad = 10;
      float scale = ClickGuiLayout.scale(width, height, panelW, panelH);
      int logicalW = ClickGuiLayout.logicalExtent(width, scale);
      int logicalH = ClickGuiLayout.logicalExtent(height, scale);
      int panelX = (logicalW - panelW) / 2, panelY = (logicalH - panelH) / 2;
      int contentY = panelY + 48;
      check(panelX >= 0 && panelY >= 0 && (panelX + panelW) * scale <= width
         && (panelY + panelH) * scale <= height, "panel fits viewport");
      for (int i = 0; i < 5; i++) {
         int x = ClickGuiLayout.cardX(panelX, i, pad, cardW, gap);
         int y = ClickGuiLayout.cardY(contentY, i, cardH, gap);
         check(x >= panelX + pad && x + cardW <= panelX + panelW - pad
            && y >= contentY && y + cardH <= panelY + panelH - pad,
            "card inside content");
         int physicalX = Math.round((x + cardW / 2) * scale);
         int physicalY = Math.round((y + cardH / 2) * scale);
         check((int)(physicalX / scale) >= x && (int)(physicalX / scale) < x + cardW
            && (int)(physicalY / scale) >= y && (int)(physicalY / scale) < y + cardH,
            "scaled pointer reaches card");
         for (int j = 0; j < i; j++) {
            int ox = ClickGuiLayout.cardX(panelX, j, pad, cardW, gap);
            int oy = ClickGuiLayout.cardY(contentY, j, cardH, gap);
            check(x >= ox + cardW || ox >= x + cardW || y >= oy + cardH || oy >= y + cardH,
               "cards never overlap");
         }
      }
      int flashX = panelX + pad, exportX = flashX + 2 * (cardW + gap);
      check(flashX + 2 * cardW + gap <= exportX && exportX + cardW <= panelX + panelW - pad,
         "notification controls separated");
      int debugY = contentY + cardH + gap;
      check(debugY >= contentY + cardH && debugY + cardH <= panelY + panelH - pad,
         "debug card does not cover export");
   }
   public static void main(String[] args) {
      screen(320, 240); screen(400, 300); screen(854, 480); screen(1920, 1080);
      screen(256, 160); // constrained scaled GUI
      System.out.println("GUI LAYOUT HARNESS OK: " + assertions + " assertions");
   }
}
