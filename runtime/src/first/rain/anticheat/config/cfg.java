package first.rain.anticheat.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Minimal config holder matching the cfg.v.* access pattern. Persists to
 * .minecraft/config/rain.properties; the ClickGUI calls save() on close.
 */
public class cfg {
   public static final Values v = new Values();
   private static final File FILE = new File("config", "rain.properties");

   public static class Values {
      public boolean detectAutoBlock = true;
      public boolean detectLegitScaffold = true;
      public boolean detectKillaura = true;
      public boolean debugMessages = false;

      public boolean flashEnabled = false;
      public int flashColor = 0xFF3B30;   // RGB, no alpha
      public int flashOpacity = 65;       // 0-100

      public boolean nametagEnabled = true;
      public int nametagColor = 0xFF3B30; // RGB, no alpha
      public int nametagOpacity = 100;    // 0-100
   }

   static {
      load();
   }

   public static void load() {
      if (!FILE.exists()) {
         return;
      }
      Properties props = new Properties();
      try (FileInputStream in = new FileInputStream(FILE)) {
         props.load(in);
      } catch (IOException ignored) {
         return;
      }
      v.detectAutoBlock = parseBool(props, "detectAutoBlock", v.detectAutoBlock);
      v.detectLegitScaffold = parseBool(props, "detectLegitScaffold", v.detectLegitScaffold);
      v.detectKillaura = parseBool(props, "detectKillaura", v.detectKillaura);
      v.debugMessages = parseBool(props, "debugMessages", v.debugMessages);
      v.flashEnabled = parseBool(props, "flashEnabled", v.flashEnabled);
      v.flashColor = parseHexColor(props, "flashColor", v.flashColor);
      v.flashOpacity = Math.max(0, Math.min(100, parseInt(props, "flashOpacity", v.flashOpacity)));
      v.nametagEnabled = parseBool(props, "nametagEnabled", v.nametagEnabled);
      v.nametagColor = parseHexColor(props, "nametagColor", v.nametagColor);
      v.nametagOpacity = Math.max(0, Math.min(100, parseInt(props, "nametagOpacity", v.nametagOpacity)));
   }

   public static void save() {
      Properties props = new Properties();
      props.setProperty("detectAutoBlock", Boolean.toString(v.detectAutoBlock));
      props.setProperty("detectLegitScaffold", Boolean.toString(v.detectLegitScaffold));
      props.setProperty("detectKillaura", Boolean.toString(v.detectKillaura));
      props.setProperty("debugMessages", Boolean.toString(v.debugMessages));
      props.setProperty("flashEnabled", Boolean.toString(v.flashEnabled));
      props.setProperty("flashColor", String.format("%06X", v.flashColor & 0xFFFFFF));
      props.setProperty("flashOpacity", Integer.toString(v.flashOpacity));
      props.setProperty("nametagEnabled", Boolean.toString(v.nametagEnabled));
      props.setProperty("nametagColor", String.format("%06X", v.nametagColor & 0xFFFFFF));
      props.setProperty("nametagOpacity", Integer.toString(v.nametagOpacity));
      File parent = FILE.getParentFile();
      if (parent != null) {
         parent.mkdirs();
      }
      try (FileOutputStream out = new FileOutputStream(FILE)) {
         props.store(out, "Rain settings");
      } catch (IOException ignored) {
      }
   }

   private static boolean parseBool(Properties props, String key, boolean def) {
      String value = props.getProperty(key);
      if (value == null) {
         return def;
      }
      return Boolean.parseBoolean(value);
   }

   private static int parseInt(Properties props, String key, int def) {
      String value = props.getProperty(key);
      if (value == null) {
         return def;
      }
      try {
         return Integer.parseInt(value.trim());
      } catch (NumberFormatException e) {
         return def;
      }
   }

   private static int parseHexColor(Properties props, String key, int def) {
      String value = props.getProperty(key);
      if (value == null) {
         return def;
      }
      try {
         return Integer.parseInt(value.trim(), 16) & 0xFFFFFF;
      } catch (NumberFormatException e) {
         return def;
      }
   }
}
