package com.useleess.carpet.tools;

import com.useleess.carpet.core.Patch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Build-time tool (never shipped): master jar + {@link OurRules} = the "rules jar" that {@link PatchMaker} makes
 * the patch for. Every rule becomes a {@code @Rule} static boolean of {@code carpet/CarpetSettings}, and its hook
 * goes into Minecraft's obfuscated code, named by the per-version data
 * {@code useleess-carpet/rules/<minecraft>.properties}:
 * <pre>
 * &lt;rule&gt;.method=&lt;class&gt; &lt;method&gt; &lt;descriptor&gt;   the method the hook starts
 * &lt;rule&gt;.skipFor=&lt;local&gt; &lt;class&gt;                  return at once while the rule is on and that local is one
 * </pre>
 * Anything missing (data line, class, method, Carpet's annotation or category) fails the build with its name.
 *
 * <pre>RulesMaker &lt;minecraft version&gt; &lt;master jar&gt; &lt;out rules jar&gt;</pre>
 */
public final class RulesMaker {
    static final String SETTINGS = "carpet/CarpetSettings";
    static final String RULE_CLASS = "carpet/CarpetSettings$Rule";
    static final String CATEGORY = "carpet/CarpetSettings$RuleCategory";

    private RulesMaker() {}

    public static void main(String[] args) throws IOException {
        Map<String, byte[]> jar = make(Patch.readAll(Paths.get(args[1])), hooks(args[0]), OurRules.ALL);
        Path out = Paths.get(args[2]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (ZipOutputStream o = new ZipOutputStream(Files.newOutputStream(out))) {
            for (Map.Entry<String, byte[]> e : jar.entrySet()) {
                o.putNextEntry(new ZipEntry(e.getKey()));
                o.write(e.getValue());
                o.closeEntry();
            }
        }
        System.out.println("rules jar: " + out + " (" + OurRules.ALL.size() + " rules of our own)");
    }

    public static Properties hooks(String minecraft) throws IOException {
        Properties p = new Properties();
        try (InputStream in = RulesMaker.class.getResourceAsStream("/useleess-carpet/rules/" + minecraft + ".properties")) {
            if (in == null) throw new IllegalStateException("no rule hook data for Minecraft " + minecraft);
            p.load(in);
        }
        return p;
    }

    /** The master jar's files with our rules in (a copy; the master map is not changed). */
    public static Map<String, byte[]> make(Map<String, byte[]> master, Properties hooks, List<OurRules.Rule> rules) {
        Map<String, byte[]> jar = new LinkedHashMap<>(master);
        ClassNode settings = read(jar, SETTINGS);
        List<String> categories = enumConstants(read(jar, CATEGORY));
        requireRuleAnnotation(read(jar, RULE_CLASS));
        MethodNode clinit = method(settings, "<clinit>", "()V");
        for (OurRules.Rule rule : rules) {
            addField(settings, clinit, rule, categories);
            String[] at = data(hooks, rule, "method", 3);
            String[] skip = data(hooks, rule, "skipFor", 2);
            if (!jar.containsKey(skip[1] + ".class")) throw new IllegalStateException("class " + skip[1] + " for " + rule.name + " not found in the master jar");
            ClassNode target = read(jar, at[0]);
            skipWhen(method(target, at[1], at[2]), rule.name, Integer.parseInt(skip[0]), skip[1]);
            jar.put(at[0] + ".class", write(target));
        }
        jar.put(SETTINGS + ".class", write(settings));
        return jar;
    }

    private static void addField(ClassNode settings, MethodNode clinit, OurRules.Rule rule, List<String> categories) {
        for (FieldNode f : settings.fields) {
            if (f.name.equals(rule.name)) throw new IllegalStateException("rule " + rule.name + " already exists in " + SETTINGS);
        }
        List<String[]> cats = new ArrayList<>();
        for (String c : rule.categories) {
            if (!categories.contains(c)) throw new IllegalStateException("rule " + rule.name + ": no Carpet category " + c + " (has " + categories + ")");
            cats.add(new String[]{"L" + CATEGORY + ";", c});
        }
        FieldNode f = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, rule.name, "Z", null, null);
        AnnotationNode a = new AnnotationNode("L" + RULE_CLASS + ";");
        a.values = new ArrayList<>(Arrays.asList("desc", rule.desc, "category", cats));
        f.visibleAnnotations = new ArrayList<>(Arrays.asList(a));
        settings.fields.add(f);
        InsnList set = new InsnList();
        set.add(new InsnNode(rule.byDefault ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        set.add(new FieldInsnNode(Opcodes.PUTSTATIC, SETTINGS, rule.name, "Z"));
        clinit.instructions.insert(set);
    }

    /** At the method's start: {@code if (CarpetSettings.<rule> && local instanceof type) return;} */
    private static void skipWhen(MethodNode m, String rule, int local, String type) {
        if (Type.getReturnType(m.desc) != Type.VOID_TYPE) throw new IllegalStateException(m.name + m.desc + " for " + rule + " does not return void");
        LabelNode go = new LabelNode();
        InsnList check = new InsnList();
        check.add(new FieldInsnNode(Opcodes.GETSTATIC, SETTINGS, rule, "Z"));
        check.add(new JumpInsnNode(Opcodes.IFEQ, go));
        check.add(new VarInsnNode(Opcodes.ALOAD, local));
        check.add(new TypeInsnNode(Opcodes.INSTANCEOF, type));
        check.add(new JumpInsnNode(Opcodes.IFEQ, go));
        check.add(new InsnNode(Opcodes.RETURN));
        check.add(go);
        AbstractInsnNode first = m.instructions.getFirst();
        while (first != null && first.getOpcode() < 0 && !(first instanceof FrameNode)) first = first.getNext();
        if (!(first instanceof FrameNode)) check.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(check);
    }

    private static void requireRuleAnnotation(ClassNode rule) {
        for (String member : new String[]{"desc", "category"}) {
            if (rule.methods.stream().noneMatch(m -> m.name.equals(member))) throw new IllegalStateException("Carpet's @Rule has no " + member + "()");
        }
    }

    private static List<String> enumConstants(ClassNode e) {
        List<String> out = new ArrayList<>();
        for (FieldNode f : e.fields) if ((f.access & Opcodes.ACC_ENUM) != 0) out.add(f.name);
        return out;
    }

    private static String[] data(Properties hooks, OurRules.Rule rule, String key, int parts) {
        String v = hooks.getProperty(rule.name + "." + key);
        String[] s = v == null ? new String[0] : v.trim().split(" +");
        if (s.length != parts) throw new IllegalStateException("rule hook data " + rule.name + "." + key + " missing or not " + parts + " words: " + v);
        return s;
    }

    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("method " + c.name + "." + name + desc + " not found in the master jar");
    }

    private static ClassNode read(Map<String, byte[]> jar, String name) {
        byte[] b = jar.get(name + ".class");
        if (b == null) throw new IllegalStateException("class " + name + " not found in the master jar");
        ClassNode c = new ClassNode();
        new ClassReader(b).accept(c, 0);
        return c;
    }

    private static byte[] write(ClassNode c) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);
        return w.toByteArray();
    }
}
