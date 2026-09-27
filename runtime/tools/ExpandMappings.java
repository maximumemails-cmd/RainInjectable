import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adds inherited SRG member references to the 1.8.9 Notch-to-SRG map. */
public final class ExpandMappings {
   private static final Pattern CLASS_IN_DESC = Pattern.compile("L([^;]+);");
   private static final Map<String, String> classToNotch = new HashMap<String, String>();
   private static final Map<String, String> fieldNameToNotch = new HashMap<String, String>();
   private static final Map<String, String> methodNameToNotch = new HashMap<String, String>();
   private static final Set<String> existingFields = new HashSet<String>();
   private static final Set<String> existingMethods = new HashSet<String>();
   private static final Set<String> extra = new HashSet<String>();

   private ExpandMappings() {}

   public static void main(String[] args) throws Exception {
      if (args.length != 3) throw new IllegalArgumentException("mapping inputJar outputMap");
      List<String> lines = new ArrayList<String>();
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8));
      try {
         String line;
         while ((line = reader.readLine()) != null) {
            lines.add(line);
            String[] p = line.split(" ");
            if (p.length >= 3 && p[0].equals("CL:")) {
               classToNotch.put(p[2], p[1]);
            } else if (p.length >= 3 && p[0].equals("FD:")) {
               existingFields.add(p[2]);
               if (!p[2].substring(p[2].lastIndexOf('/') + 1).startsWith("field_")) continue;
               String fieldName = p[2].substring(p[2].lastIndexOf('/') + 1);
               String notchField = p[1].substring(p[1].lastIndexOf('/') + 1);
               String old = fieldNameToNotch.get(fieldName);
               fieldNameToNotch.put(fieldName, old != null && !old.equals(notchField) ? "" : notchField);
            } else if (p.length >= 5 && p[0].equals("MD:")) {
               existingMethods.add(p[3] + " " + p[4]);
               String key = p[3].substring(p[3].lastIndexOf('/') + 1) + " " + p[4];
               if (!key.startsWith("func_")) continue;
               String notchName = p[1].substring(p[1].lastIndexOf('/') + 1);
               String old = methodNameToNotch.get(key);
               methodNameToNotch.put(key, old != null && !old.equals(notchName) ? "" : notchName);
            }
         }
      } finally { reader.close(); }

      ZipFile jar = new ZipFile(new File(args[1]));
      try {
         Enumeration<? extends ZipEntry> entries = jar.entries();
         while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;
            InputStream in = jar.getInputStream(entry);
            try { scan(new ClassReader(in)); }
            finally { in.close(); }
         }
      } finally { jar.close(); }

      Writer out = new OutputStreamWriter(new FileOutputStream(args[2]), StandardCharsets.UTF_8);
      try {
         for (String line : lines) { out.write(line); out.write('\n'); }
         for (String line : extra) { out.write(line); out.write('\n'); }
      } finally { out.close(); }
      System.out.println("Expanded inherited mappings: " + extra.size());
   }

   private static void scan(ClassReader reader) {
      reader.accept(new ClassVisitor(Opcodes.ASM5) {
         @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                     String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM5) {
               @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                  if (!owner.startsWith("net/minecraft/") || !name.startsWith("field_")) return;
                  String key = owner + "/" + name;
                  if (existingFields.contains(key)) return;
                  String notchOwner = classToNotch.get(owner);
                  String notchName = fieldNameToNotch.get(name);
                  if (notchOwner == null || notchName == null || notchName.isEmpty()) throw new IllegalStateException("Unmapped field " + key);
                  extra.add("FD: " + notchOwner + "/" + notchName + " " + key);
               }
               @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                  if (!owner.startsWith("net/minecraft/") || !name.startsWith("func_")) return;
                  String key = owner + "/" + name + " " + desc;
                  if (existingMethods.contains(key)) return;
                  String notchOwner = classToNotch.get(owner);
                  String notchName = methodNameToNotch.get(name + " " + desc);
                  if (notchOwner == null || notchName == null || notchName.isEmpty()) throw new IllegalStateException("Unmapped method " + key);
                  extra.add("MD: " + notchOwner + "/" + notchName + " " + notchDesc(desc) +
                     " " + owner + "/" + name + " " + desc);
               }
            };
         }
      }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
   }

   private static String notchDesc(String desc) {
      Matcher matcher = CLASS_IN_DESC.matcher(desc);
      StringBuffer out = new StringBuffer();
      while (matcher.find()) {
         String mapped = classToNotch.get(matcher.group(1));
         matcher.appendReplacement(out, Matcher.quoteReplacement("L" + (mapped == null ? matcher.group(1) : mapped) + ";"));
      }
      matcher.appendTail(out);
      return out.toString();
   }
}
