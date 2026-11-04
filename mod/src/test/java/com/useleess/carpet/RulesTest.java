package com.useleess.carpet;

import com.useleess.carpet.core.Patch;
import com.useleess.carpet.tools.OurRules;
import com.useleess.carpet.tools.RulesMaker;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Our own Carpet rules in the rules jar that makeRules built from the master jar (the jar the patch is made for). */
class RulesTest {
    static Map<String, byte[]> jar(String property) throws IOException {
        return Patch.readAll(Paths.get(System.getProperty(property)));
    }

    static ClassNode node(Map<String, byte[]> jar, String name) {
        ClassNode c = new ClassNode();
        new ClassReader(jar.get(name + ".class")).accept(c, 0);
        return c;
    }

    static List<AbstractInsnNode> code(MethodNode m) {
        List<AbstractInsnNode> out = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) if (i.getOpcode() >= 0) out.add(i);
        return out;
    }

    static MethodNode method(ClassNode c, String name, String desc) {
        return c.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(AssertionError::new);
    }

    static String[] hook(String rule, String key) throws IOException {
        return RulesMaker.hooks("1.12.2").getProperty(rule + "." + key).split(" ");
    }

    @Test void everyRuleIsACarpetRuleFieldWithItsDefault() throws IOException {
        ClassNode settings = node(jar("useleess.rulesJar"), "carpet/CarpetSettings");
        List<AbstractInsnNode> clinit = code(method(settings, "<clinit>", "()V"));
        for (OurRules.Rule rule : OurRules.ALL) {
            FieldNode f = settings.fields.stream().filter(x -> x.name.equals(rule.name)).findFirst().orElseThrow(() -> new AssertionError(rule.name));
            assertEquals("Z", f.desc);
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, f.access);
            AnnotationNode a = f.visibleAnnotations.get(0);
            assertEquals("Lcarpet/CarpetSettings$Rule;", a.desc);
            assertEquals(rule.desc, a.values.get(a.values.indexOf("desc") + 1));
            List<String> cats = new ArrayList<>();
            for (Object c : (List<?>) a.values.get(a.values.indexOf("category") + 1)) cats.add(((String[]) c)[1]);
            assertEquals(rule.categories, cats);
            int set = -1;
            for (int i = 1; i < clinit.size(); i++) {
                if (clinit.get(i) instanceof FieldInsnNode && ((FieldInsnNode) clinit.get(i)).name.equals(rule.name)) set = i;
            }
            assertTrue(set > 0, rule.name + " is set in <clinit>");
            assertEquals(rule.byDefault ? Opcodes.ICONST_1 : Opcodes.ICONST_0, clinit.get(set - 1).getOpcode(), rule.name + " default");
        }
    }

    @Test void endPortalMethodStartsWithThePlayerCheck() throws IOException {
        String[] at = hook("endPortalNoTeleportPlayer", "method");
        String[] skip = hook("endPortalNoTeleportPlayer", "skipFor");
        assertEquals(Arrays.asList("aqh", "a", "(Lamu;Let;Lawt;Lvg;)V"), Arrays.asList(at));
        Map<String, byte[]> rules = jar("useleess.rulesJar");
        List<AbstractInsnNode> c = code(method(node(rules, at[0]), at[1], at[2]));
        FieldInsnNode rule = (FieldInsnNode) c.get(0);
        assertEquals(Opcodes.GETSTATIC, rule.getOpcode());
        assertEquals("carpet/CarpetSettings", rule.owner);
        assertEquals("endPortalNoTeleportPlayer", rule.name);
        assertEquals(Opcodes.IFEQ, c.get(1).getOpcode());
        assertEquals(Integer.parseInt(skip[0]), ((VarInsnNode) c.get(2)).var);
        assertEquals(Opcodes.INSTANCEOF, c.get(3).getOpcode());
        assertEquals("aed", ((TypeInsnNode) c.get(3)).desc);
        assertEquals(Opcodes.IFEQ, c.get(4).getOpcode());
        assertEquals(Opcodes.RETURN, c.get(5).getOpcode());
        // the rest is the master jar's method unchanged
        List<AbstractInsnNode> original = code(method(node(jar("useleess.masterJar"), at[0]), at[1], at[2]));
        assertEquals(original.size(), c.size() - 6);
        for (int i = 0; i < original.size(); i++) assertEquals(original.get(i).getOpcode(), c.get(i + 6).getOpcode());
    }

    /**
     * The rules jar is the master jar with exactly these changes: the rule hook (aqh), the rules (CarpetSettings), the
     * server hooks (CarpetServer, MinecraftServer, PlayerList, EntityPlayerMP), and our own classes and language file added.
     */
    @Test void onlyOurChangesAreInTheRulesJar() throws IOException {
        Map<String, byte[]> master = jar("useleess.masterJar");
        Map<String, byte[]> rules = jar("useleess.rulesJar");
        List<String> masterKeys = new ArrayList<>(master.keySet());
        assertEquals(masterKeys, new ArrayList<>(rules.keySet()).subList(0, masterKeys.size()), "the master jar's files keep their order");
        List<String> changed = new ArrayList<>();
        for (String name : master.keySet()) if (!Arrays.equals(master.get(name), rules.get(name))) changed.add(name);
        Collections.sort(changed);
        assertTrue(changed.containsAll(Arrays.asList("aqh.class", "carpet/CarpetSettings.class", "carpet/CarpetServer.class", "net/minecraft/server/MinecraftServer.class", "aow.class", "carpet/logging/LoggerRegistry.class")), changed.toString());
        assertEquals(9, changed.size(), "aqh, CarpetSettings, CarpetServer, MinecraftServer, PlayerList, the player class, the player's network handler (pa), aow and LoggerRegistry: " + changed);
        for (String name : rules.keySet()) {
            if (master.containsKey(name)) continue;
            assertTrue(name.startsWith("com/useleess/carpet/server/") || name.startsWith("com/useleess/carpet/core/")
                    || name.equals("assets/useleess_carpet/lang/en_us.json"), "added: " + name);
        }
        assertTrue(rules.containsKey("com/useleess/carpet/server/Hooks.class"));
        assertTrue(rules.containsKey("assets/useleess_carpet/lang/en_us.json"));
    }

    @Test void theServerHooksCallOurHooksClass() throws IOException {
        Map<String, byte[]> master = jar("useleess.masterJar");
        Map<String, byte[]> rules = jar("useleess.rulesJar");
        List<String> called = new ArrayList<>();
        for (String name : master.keySet()) {
            if (!name.endsWith(".class") || Arrays.equals(master.get(name), rules.get(name))) continue;
            for (MethodNode m : node(rules, name.substring(0, name.length() - 6)).methods) {
                for (AbstractInsnNode i : code(m)) {
                    if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.equals("com/useleess/carpet/server/Hooks")) called.add(((MethodInsnNode) i).name);
                }
            }
        }
        Collections.sort(called);
        assertEquals(Arrays.asList("clickDone", "clickStarted", "customPayload", "onGround", "playerDying", "playerJoined", "playerLeft", "serverLoaded", "serverStopping", "tick"), called);
    }

    @Test void customRecipesIsADefaultOnRuleWithoutAHook() throws IOException {
        assertTrue(OurRules.ALL.stream().anyMatch(r -> r.name.equals("customRecipes") && r.byDefault && !r.hooked));
        assertNull(RulesMaker.hooks("1.12.2").getProperty("customRecipes.method"));
    }

    @Test void redstoneScopeLoggerIsADefaultOffRuleWithoutAHook() throws IOException {
        assertTrue(OurRules.ALL.stream().anyMatch(r -> r.name.equals("redstoneScopeLogger") && !r.byDefault && !r.hooked));
        assertNull(RulesMaker.hooks("1.12.2").getProperty("redstoneScopeLogger.method"));
    }

    @Test void antiNoFallIsADefaultOffSurvivalRuleWithoutADataHook() throws IOException {
        assertTrue(OurRules.ALL.stream().anyMatch(r -> r.name.equals("antiNoFall") && !r.byDefault && !r.hooked && r.categories.contains("SURVIVAL")));
        assertNull(RulesMaker.hooks("1.12.2").getProperty("antiNoFall.method"));
    }

    @Test void litematicaShulkerRestockIsADefaultOffRuleWithoutADataHook() throws IOException {
        assertTrue(OurRules.ALL.stream().anyMatch(r -> r.name.equals("litematicaShulkerRestock") && !r.byDefault && !r.hooked));
        assertNull(RulesMaker.hooks("1.12.2").getProperty("litematicaShulkerRestock.method"));
    }

    /** handleFalling starts with {@code onGround = Hooks.onGround(this, onGround)}; the rest is the original. */
    @Test void handleFallingFirstAsksOurHookAboutTheGround() throws IOException {
        String method = null;
        for (MethodNode m : node(jar("useleess.rulesJar"), "oq").methods) {
            AbstractInsnNode[] code = code(m).toArray(new AbstractInsnNode[0]);
            if (code.length < 4 || !(code[2] instanceof MethodInsnNode) || !((MethodInsnNode) code[2]).name.equals("onGround")) continue;
            assertNull(method, "one method only");
            method = m.name + m.desc;
            assertEquals(Opcodes.ALOAD, code[0].getOpcode());
            assertEquals(0, ((VarInsnNode) code[0]).var);
            assertEquals(Opcodes.ILOAD, code[1].getOpcode());
            assertEquals(3, ((VarInsnNode) code[1]).var);
            assertEquals("com/useleess/carpet/server/Hooks", ((MethodInsnNode) code[2]).owner);
            assertEquals("(Loq;Z)Z", ((MethodInsnNode) code[2]).desc);
            assertEquals(Opcodes.ISTORE, code[3].getOpcode());
            assertEquals(3, ((VarInsnNode) code[3]).var);
        }
        assertEquals("a(DZ)V", method, "EntityPlayerMP.handleFalling (oq a (DZ)V)");
    }

    /** A missing target never builds a jar without the rule: the build stops and names what is missing. */
    @Test void aMissingTargetFailsWithItsName() throws IOException {
        Map<String, byte[]> master = jar("useleess.masterJar");
        Properties ok = RulesMaker.hooks("1.12.2");
        assertFails(master, with(ok, "endPortalNoTeleportPlayer.method", "aqh a (Lnope;)V"), "aqh.a(Lnope;)V not found");
        assertFails(master, with(ok, "endPortalNoTeleportPlayer.method", "zzzq a (Lamu;Let;Lawt;Lvg;)V"), "class zzzq not found");
        assertFails(master, with(ok, "endPortalNoTeleportPlayer.skipFor", "4 zzzq"), "class zzzq for endPortalNoTeleportPlayer not found");
        Properties none = new Properties();
        assertFails(master, none, "endPortalNoTeleportPlayer.method missing");
        OurRules.Rule badCategory = new OurRules.Rule("endPortalNoTeleportPlayer", false, "x", "NOPE");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RulesMaker.make(master, ok, Collections.singletonList(badCategory)));
        assertTrue(e.getMessage().contains("no Carpet category NOPE"), e.getMessage());
        IllegalStateException twice = assertThrows(IllegalStateException.class,
                () -> RulesMaker.make(jar("useleess.rulesJar"), ok, OurRules.ALL));
        assertTrue(twice.getMessage().contains("already exists"), twice.getMessage());
        assertThrows(IllegalStateException.class, () -> RulesMaker.hooks("0.0.0"));
    }

    @Test void aDefaultOnRuleIsSetTrue() throws IOException {
        Map<String, byte[]> master = jar("useleess.masterJar");
        Properties hooks = with(RulesMaker.hooks("1.12.2"), "testOn.method", "aqh a (Lamu;Let;Lawt;Lvg;)V");
        hooks.setProperty("testOn.skipFor", "4 aed");
        Map<String, byte[]> out = RulesMaker.make(master, hooks, Collections.singletonList(new OurRules.Rule("testOn", true, "t", "CREATIVE")));
        List<AbstractInsnNode> clinit = code(method(node(out, "carpet/CarpetSettings"), "<clinit>", "()V"));
        assertEquals(Opcodes.ICONST_1, clinit.get(0).getOpcode());
        assertEquals("testOn", ((FieldInsnNode) clinit.get(1)).name);
    }

    static Properties with(Properties base, String key, String value) {
        Properties p = new Properties();
        p.putAll(base);
        p.setProperty(key, value);
        return p;
    }

    static void assertFails(Map<String, byte[]> master, Properties hooks, String expected) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RulesMaker.make(master, hooks, OurRules.ALL));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }
}
