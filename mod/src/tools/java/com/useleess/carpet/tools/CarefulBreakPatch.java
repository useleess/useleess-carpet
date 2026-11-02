package com.useleess.carpet.tools;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Build-time tool (never shipped): careful break per player, without a new Carpet rule. Two patches of the master jar:
 * Carpet's logger {@code carefulBreak} gets the options {@code sneak} (default) and {@code always} (the two nulls of
 * its registration), and in the block class that drops the item into the miner's inventory the one Sneak check
 * ({@code isSneaking()} of the miner) becomes {@code CarefulBreak.wants(miner)}. A missing site, or one found more
 * than once, fails the build with a clear message. The per-version names are in the rules data
 * ({@code carefulBreak.class}, {@code carefulBreak.sneakCall}).
 */
public final class CarefulBreakPatch {
    static final String REGISTRY = "carpet/logging/LoggerRegistry";
    static final String LOGGER = "carefulBreak";
    static final String HELPER = ServerLayer.SERVER_PACKAGE + "CarefulBreak";

    private CarefulBreakPatch() {}

    public static void apply(Map<String, byte[]> jar, Properties hooks) {
        ClassNode registry = RulesMaker.read(jar, REGISTRY);
        options(registry);
        jar.put(REGISTRY + ".class", RulesMaker.write(registry));
        String[] data = RulesMaker.data(hooks, LOGGER, "class", 1);
        String[] call = RulesMaker.data(hooks, LOGGER, "sneakCall", 3);
        ClassNode drop = RulesMaker.read(jar, data[0]);
        replaceSneakCall(drop, call);
        jar.put(data[0] + ".class", RulesMaker.write(drop));
    }

    /** {@code ldc "carefulBreak"; aconst_null; aconst_null} (the default option and the options of the registration) gets the real values. */
    private static void options(ClassNode registry) {
        List<AbstractInsnNode> sites = new ArrayList<>();
        MethodNode in = null;
        for (MethodNode m : registry.methods) {
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof LdcInsnNode && LOGGER.equals(((LdcInsnNode) i).cst) && next(i, 1) != null && next(i, 1).getOpcode() == Opcodes.ACONST_NULL
                        && next(i, 2) != null && next(i, 2).getOpcode() == Opcodes.ACONST_NULL) {
                    sites.add(i);
                    in = m;
                }
            }
        }
        if (sites.size() != 1) {
            throw new IllegalStateException("careful break: the registration of logger " + LOGGER + " (name, null, null) was found "
                    + sites.size() + " times in " + REGISTRY + ", expected once");
        }
        AbstractInsnNode name = sites.get(0);
        AbstractInsnNode defaultOption = next(name, 1);
        AbstractInsnNode optionList = next(name, 2);
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.ICONST_2));
        list.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new InsnNode(Opcodes.ICONST_0));
        list.add(new LdcInsnNode("sneak"));
        list.add(new InsnNode(Opcodes.AASTORE));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new InsnNode(Opcodes.ICONST_1));
        list.add(new LdcInsnNode("always"));
        list.add(new InsnNode(Opcodes.AASTORE));
        in.instructions.insertBefore(defaultOption, new LdcInsnNode("sneak"));
        in.instructions.remove(defaultOption);
        in.instructions.insertBefore(optionList, list);
        in.instructions.remove(optionList);
    }

    /** The n-th real instruction after {@code from} (labels and line numbers skipped). */
    private static AbstractInsnNode next(AbstractInsnNode from, int n) {
        AbstractInsnNode i = from;
        for (int k = 0; k < n && i != null; k++) {
            i = i.getNext();
            while (i != null && i.getOpcode() < 0) i = i.getNext();
        }
        return i;
    }

    private static void replaceSneakCall(ClassNode drop, String[] call) {
        List<MethodInsnNode> sites = new ArrayList<>();
        MethodNode in = null;
        for (MethodNode m : drop.methods) {
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof MethodInsnNode && i.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode c = (MethodInsnNode) i;
                    if (c.owner.equals(call[0]) && c.name.equals(call[1]) && c.desc.equals(call[2])) {
                        sites.add(c);
                        in = m;
                    }
                }
            }
        }
        if (sites.size() != 1) {
            throw new IllegalStateException("careful break: the Sneak check " + call[0] + "." + call[1] + call[2] + " was found "
                    + sites.size() + " times in " + drop.name + ", expected once");
        }
        MethodInsnNode old = sites.get(0);
        in.instructions.set(old, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "wants", "(L" + call[0] + ";)Z", false));
    }
}
