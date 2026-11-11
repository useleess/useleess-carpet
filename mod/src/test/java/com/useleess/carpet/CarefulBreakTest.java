package com.useleess.carpet;

import com.useleess.carpet.server.ChoiceStore;
import com.useleess.carpet.tools.CarefulBreakPatch;
import com.useleess.carpet.tools.RulesMaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Careful break per player: the two patches of the rules jar and the saved choices (no game). */
class CarefulBreakTest {
    static final String REGISTRY = "carpet/logging/LoggerRegistry";
    static final String HELPER = "com/useleess/carpet/server/CarefulBreak";

    static int count(ClassNode c, int opcode) {
        int n = 0;
        for (MethodNode m : c.methods) for (AbstractInsnNode i : m.instructions) if (i.getOpcode() == opcode) n++;
        return n;
    }

    static int strings(ClassNode c, String text) {
        int n = 0;
        for (MethodNode m : c.methods) for (AbstractInsnNode i : m.instructions) if (i instanceof LdcInsnNode && text.equals(((LdcInsnNode) i).cst)) n++;
        return n;
    }

    static int calls(ClassNode c, String owner, String name, int opcode) {
        int n = 0;
        for (MethodNode m : c.methods) {
            for (AbstractInsnNode i : m.instructions) {
                if (i.getOpcode() == opcode && ((MethodInsnNode) i).owner.equals(owner) && ((MethodInsnNode) i).name.equals(name)) n++;
            }
        }
        return n;
    }

    @Test void theLoggerRegistrationGetsSneakAsDefaultAndTheTwoOptions() throws IOException {
        ClassNode master = RulesTest.node(RulesTest.jar("useleess.masterJar"), REGISTRY);
        ClassNode rules = RulesTest.node(RulesTest.jar("useleess.rulesJar"), REGISTRY);
        assertEquals(strings(master, "sneak") + 2, strings(rules, "sneak"), "the default option and the first of the list");
        assertEquals(strings(master, "always") + 1, strings(rules, "always"));
        assertEquals(count(master, Opcodes.ACONST_NULL) - 2, count(rules, Opcodes.ACONST_NULL), "only the two nulls of carefulBreak are replaced");
        assertEquals(count(master, Opcodes.ANEWARRAY) + 1, count(rules, Opcodes.ANEWARRAY));
    }

    @Test void theSneakCheckOfTheDropCodeIsReplacedOnce() throws IOException {
        Properties data = RulesMaker.hooks("1.12.2");
        String[] call = data.getProperty("carefulBreak.sneakCall").split(" ");
        String block = data.getProperty("carefulBreak.class");
        ClassNode master = RulesTest.node(RulesTest.jar("useleess.masterJar"), block);
        ClassNode rules = RulesTest.node(RulesTest.jar("useleess.rulesJar"), block);
        assertEquals(1, calls(master, call[0], call[1], Opcodes.INVOKEVIRTUAL));
        assertEquals(0, calls(rules, call[0], call[1], Opcodes.INVOKEVIRTUAL));
        assertEquals(1, calls(rules, HELPER, "wants", Opcodes.INVOKESTATIC));
        for (MethodNode m : rules.methods) {
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("wants")) assertEquals("(L" + call[0] + ";)Z", ((MethodInsnNode) i).desc);
            }
        }
    }

    @Test void theHelperTakesThePlayerClassTheCallSiteHas() throws IOException {
        String[] call = RulesMaker.hooks("1.12.2").getProperty("carefulBreak.sneakCall").split(" ");
        ClassNode helper = RulesTest.node(RulesTest.jar("useleess.rulesJar"), HELPER);
        MethodNode wants = RulesTest.method(helper, "wants", "(L" + call[0] + ";)Z");
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, wants.access);
    }

    @Test void aMissingOrRepeatedSiteFailsTheBuildWithAClearMessage() throws IOException {
        Map<String, byte[]> master = RulesTest.jar("useleess.masterJar");
        Properties ok = RulesMaker.hooks("1.12.2");
        assertFailsWith(master, RulesTest.with(ok, "carefulBreak.sneakCall", "oq zz ()Z"), "oq.zz()Z was found 0 times");
        assertFailsWith(master, RulesTest.with(ok, "carefulBreak.class", "zzzq"), "class zzzq not found");
        Properties none = new Properties();
        none.putAll(ok);
        none.remove("carefulBreak.class");
        assertFailsWith(master, none, "carefulBreak.class missing");

        Map<String, byte[]> twice = new LinkedHashMap<>(master);
        twice.put("t/Drop.class", dropClass("t/Drop", 2));
        assertFailsWith(twice, RulesTest.with(RulesTest.with(ok, "carefulBreak.class", "t/Drop"), "carefulBreak.sneakCall", "t/P s ()Z"), "was found 2 times in t/Drop");

        Map<String, byte[]> noRegistration = new LinkedHashMap<>(master);
        noRegistration.put(REGISTRY + ".class", dropClass(REGISTRY, 0));
        assertFailsWith(noRegistration, ok, "was found 0 times in " + REGISTRY);
    }

    static void assertFailsWith(Map<String, byte[]> jar, Properties hooks, String expected) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CarefulBreakPatch.apply(new LinkedHashMap<>(jar), hooks));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    /** A class with one method that calls {@code t/P.s()Z} the given number of times. */
    static byte[] dropClass(String name, int sneakCalls) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "drop", "(Lt/P;)V", null, null);
        m.visitCode();
        for (int i = 0; i < sneakCalls; i++) {
            m.visitVarInsn(Opcodes.ALOAD, 1);
            m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "t/P", "s", "()Z", false);
            m.visitInsn(Opcodes.POP);
        }
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    @Test void theSavedChoicesSurviveARestart(@TempDir Path dir) throws IOException {
        File file = new File(dir.toFile(), "useleess-carpet/careful-break.json");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ChoiceStore store = ChoiceStore.open(file);
        assertNull(store.get(a));
        store.set(a, "always");
        store.set(b, "sneak");
        ChoiceStore again = ChoiceStore.open(file);
        assertEquals("always", again.get(a));
        assertEquals("sneak", again.get(b));
        again.set(a, null);
        ChoiceStore third = ChoiceStore.open(file);
        assertNull(third.get(a));
        assertEquals("sneak", third.get(b));
        assertFalse(new File(file.getParentFile(), "careful-break.json.tmp").exists());
    }

    @Test void aDamagedFileIsRefusedNotCrashed(@TempDir Path dir) throws IOException {
        File file = new File(dir.toFile(), "careful-break.json");
        Files.write(file.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> ChoiceStore.open(file));
        Files.write(file.toPath(), "{\"not-a-uuid\": \"always\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> ChoiceStore.open(file));
        Files.write(file.toPath(), "[1,2]".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> ChoiceStore.open(file));
    }
}
