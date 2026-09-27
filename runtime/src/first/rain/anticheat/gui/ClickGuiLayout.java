package first.rain.anticheat.gui;

/** Scale-independent geometry shared by rendering, hit testing and layout tests. */
public final class ClickGuiLayout {
   private ClickGuiLayout() {}
   public static float scale(int screenWidth, int screenHeight, int panelWidth, int panelHeight) {
      if (screenWidth <= 0 || screenHeight <= 0) return 1.0F;
      return Math.min(1.0F, Math.min((float)screenWidth / (panelWidth + 8),
         (float)screenHeight / (panelHeight + 8)));
   }
   public static int logicalExtent(int screenExtent, float scale) {
      return Math.round(screenExtent / scale);
   }
   public static int cardX(int panelX, int index, int padding, int width, int gap) {
      return panelX + padding + (index % 3) * (width + gap);
   }
   public static int cardY(int contentY, int index, int height, int gap) {
      return contentY + (index / 3) * (height + gap);
   }
}
