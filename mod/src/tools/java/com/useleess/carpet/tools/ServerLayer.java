package com.useleess.carpet.tools;

import com.redstonescope.carpetbuild.NameBridge;
import com.useleess.carpet.core.Patch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Build-time tool (never shipped): adds the server code of {@code src/server} to the jar. It is written and compiled
 * against Minecraft's readable (MCP) names; here it is turned into SRG names ({@link McpNames}) and from those into
 * the server's obfuscated names (Redstone Scope's {@code Mappings}), the few hooks into Carpet and Minecraft are
 * patched in (written in SRG names, the same way), and {@link LinkCheck} proves every link holds. Any failure fails
 * the build with the names it found.
 */
final class ServerLayer {
    static final String SERVER_PACKAGE = "com/useleess/carpet/server/";
    static final String CORE_PACKAGE = "com/useleess/carpet/core/";
    static final String LANG = "assets/useleess_carpet/lang/en_us.json";
    private static final String HOOKS = SERVER_PACKAGE + "Hooks";
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String PLAYER_LIST = "net/minecraft/server/management/PlayerList";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayerMP";
    private static final String NET_HANDLER = "net/minecraft/network/NetHandlerPlayServer";
    private static final String POTION_HELPER = "net/minecraft/potion/PotionHelper";
    private static final String SERVER_DESC = "(L" + SERVER + ";)V";
    private static final String PLAYER_DESC = "(L" + PLAYER + ";)V";

    /** The inputs of {@link #add}, all paths. */
    static final class Inputs {
        final Path serverClasses, coreClasses, lang, mappedJar, obfToSrg, srgToMcp;

        Inputs(String[] a) {
            serverClasses = java.nio.file.Paths.get(a[0]);
            coreClasses = java.nio.file.Paths.get(a[1]);
            lang = java.nio.file.Paths.get(a[2]);
            mappedJar = java.nio.file.Paths.get(a[3]);
            obfToSrg = java.nio.file.Paths.get(a[4]);
            srgToMcp = java.nio.file.Paths.get(a[5]);
        }
    }

    private ServerLayer() {}

    static void add(Map<String, byte[]> jar, Inputs in) throws IOException {
        NameBridge names = new NameBridge(in.obfToSrg, jar);
        Map<String, byte[]> mine = classes(in.serverClasses, SERVER_PACKAGE);
        Map<String, byte[]> hierarchy = new LinkedHashMap<>(Patch.readAll(in.mappedJar));
        hierarchy.putAll(mine);
        McpNames mcp = new McpNames(in.srgToMcp, hierarchy);
        List<String> touched = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : mine.entrySet()) {
            jar.put(e.getKey(), names.toObf(mcp.toSrg(e.getValue())));
            touched.add(e.getKey());
        }
        for (Map.Entry<String, byte[]> e : classes(in.coreClasses, CORE_PACKAGE).entrySet()) {
            jar.put(e.getKey(), e.getValue());
            touched.add(e.getKey());
        }
        jar.put(LANG, Files.readAllBytes(in.lang));
        hook(jar, names, "carpet/CarpetServer", "onServerLoaded", SERVER_DESC, false, "serverLoaded", SERVER_DESC, 0);
        hook(jar, names, "carpet/CarpetServer", "tick", SERVER_DESC, true, "tick", SERVER_DESC, 0);
        hook(jar, names, "carpet/CarpetServer", "playerConnected", PLAYER_DESC, true, "playerJoined", PLAYER_DESC, 0);
        // PlayerList.playerLoggedOut (the player is its first argument): before the player data is written
        hook(jar, names, PLAYER_LIST, "func_72367_e", PLAYER_DESC, true, "playerLeft", PLAYER_DESC, 1);
        hook(jar, names, SERVER, "func_71260_j", "()V", true, "serverStopping", SERVER_DESC, 0);
        hook(jar, names, PLAYER, "func_70645_a", "(Lnet/minecraft/util/DamageSource;)V", true, "playerDying", PLAYER_DESC, 0);
        // NetHandlerPlayServer.processCustomPayload: Litematica Upgraded's messages
        hook(jar, names, NET_HANDLER, "func_147349_a", "(Lnet/minecraft/network/play/client/CPacketCustomPayload;)V", true, "customPayload",
                "(L" + NET_HANDLER + ";Lnet/minecraft/network/play/client/CPacketCustomPayload;)V", 0, 1);
        // NetHandlerPlayServer.processTryUseItemOnBlock: which blocks a click placed, for Litematica Upgraded's accurate placement
        String click = "(Lnet/minecraft/network/play/client/CPacketPlayerTryUseItemOnBlock;)V", handlerDesc = "(L" + NET_HANDLER + ";)V";
        hook(jar, names, NET_HANDLER, "func_184337_a", click, true, "clickStarted", handlerDesc, 0);
        hook(jar, names, NET_HANDLER, "func_184337_a", click, false, "clickDone", handlerDesc, 0);
        // EntityPlayerMP.handleFalling(double, boolean): the client's "on the ground" (local 3) passes Hooks.onGround first
        filter(jar, names, PLAYER, "func_71122_b", "(DZ)V", 3, "onGround", "(L" + PLAYER + ";Z)Z");
        // furnace + brewing stand recipe lookups (#7 #8): a saved recipe answers first, else Minecraft's own code runs
        String stack = "Lnet/minecraft/item/ItemStack;", stackToStack = "(" + stack + ")" + stack, twoStacks = "(" + stack + stack + ")";
        answer(jar, names, "net/minecraft/item/crafting/FurnaceRecipes", "func_151395_a", stackToStack, "smeltResult", stackToStack, 1);
        answer(jar, names, POTION_HELPER, "func_185205_a", "(" + stack + ")Z", "brewIngredient", "(" + stack + ")Z", 0);
        answer(jar, names, POTION_HELPER, "func_185208_a", twoStacks + "Z", "brews", twoStacks + "Z", 0, 1);
        answer(jar, names, POTION_HELPER, "func_185212_d", twoStacks + stack, "brewResult", twoStacks + stack, 0, 1);
        TreeSet<String> broken = new LinkCheck(jar).check(touched);
        if (!broken.isEmpty()) {
            throw new IllegalStateException("links that would break on the server:\n  " + String.join("\n  ", broken));
        }
    }

    /** All .class files under a package folder of a classes directory, by jar entry name. */
    static Map<String, byte[]> classes(Path dir, String packagePath) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        Path root = dir.resolve(packagePath);
        if (!Files.isDirectory(root)) throw new IllegalStateException("no compiled classes in " + root);
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".class")).sorted().collect(Collectors.toList());
        }
        for (Path p : files) out.put(dir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
        return out;
    }

    /**
     * Calls {@code Hooks.<hook>(locals...)} at a method's start or before each of its returns (see {@link #patch}); the
     * Carpet and Minecraft classes are not link-checked (they are the originals plus one call).
     */
    private static void hook(Map<String, byte[]> jar, NameBridge names, String owner, String method, String desc, boolean atStart, String hook, String hookDesc, int... locals) {
        patch(jar, names, owner, method, desc, target -> {
            if (atStart) {
                target.instructions.insert(call(hook, hookDesc, locals));
            } else {
                for (AbstractInsnNode insn : target.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) target.instructions.insertBefore(insn, call(hook, hookDesc, locals));
                }
            }
        });
    }

    /** At a method's start: {@code arg = Hooks.<hook>(this, arg)} for its boolean argument in {@code local}. */
    private static void filter(Map<String, byte[]> jar, NameBridge names, String owner, String method, String desc, int local, String hook, String hookDesc) {
        patch(jar, names, owner, method, desc, target -> {
            InsnList list = new InsnList();
            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
            list.add(new VarInsnNode(Opcodes.ILOAD, local));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, hookDesc, false));
            list.add(new VarInsnNode(Opcodes.ISTORE, local));
            target.instructions.insert(list);
        });
    }

    /**
     * At a method's start: {@code Hooks.<hook>(locals...)}; its answer (an object that is not null, or true) is
     * returned at once, else the method runs as before. The jump gets its stack map frame here (nothing else changes).
     */
    private static void answer(Map<String, byte[]> jar, NameBridge names, String owner, String method, String desc, String hook, String hookDesc, int... locals) {
        patch(jar, names, owner, method, desc, target -> {
            InsnList list = call(hook, hookDesc, locals);
            LabelNode own = new LabelNode();
            Type answer = Type.getReturnType(hookDesc);
            if (answer.getSort() == Type.BOOLEAN) {
                list.add(new JumpInsnNode(Opcodes.IFEQ, own));
                list.add(new InsnNode(Opcodes.ICONST_1));
                list.add(new InsnNode(Opcodes.IRETURN));
                list.add(own);
                list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                list.add(new InsnNode(Opcodes.NOP));
            } else {
                list.add(new InsnNode(Opcodes.DUP));
                list.add(new JumpInsnNode(Opcodes.IFNULL, own));
                list.add(new InsnNode(Opcodes.ARETURN));
                list.add(own);
                list.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[]{answer.getInternalName()}));
                list.add(new InsnNode(Opcodes.POP));
            }
            target.instructions.insert(list);
        });
    }

    /**
     * Changes one method of a server class. The method is found by its SRG name; the change (written in readable names)
     * goes straight into the original obfuscated class, and only the instructions it added get obfuscated names, so
     * Minecraft's own code is never translated (a round trip through SRG names can lose names, e.g. {@code Ingredient.apply}).
     */
    private static void patch(Map<String, byte[]> jar, NameBridge names, String owner, String method, String desc, Consumer<MethodNode> change) {
        String entry = names.obfClass(owner) + ".class";
        byte[] original = jar.get(entry);
        if (original == null) throw new IllegalStateException("the jar has no " + entry);
        ClassNode readable = new ClassNode(), type = new ClassNode();
        new ClassReader(names.toSrg(original)).accept(readable, 0);
        new ClassReader(original).accept(type, 0);
        int at = -1;
        for (int i = 0; i < readable.methods.size(); i++) if (readable.methods.get(i).name.equals(method) && readable.methods.get(i).desc.equals(desc)) at = i;
        if (at < 0) throw new IllegalStateException(owner + "." + method + desc + " not found in the jar");
        MethodNode target = type.methods.get(at);
        Set<AbstractInsnNode> own = new HashSet<>(Arrays.asList(target.instructions.toArray()));
        change.accept(target);
        Remapper obf = new Remapper() {
            @Override public String map(String internalName) {
                return names.obfClass(internalName);
            }
        };
        for (AbstractInsnNode insn : target.instructions) {
            if (own.contains(insn)) continue;
            if (insn instanceof MethodInsnNode) ((MethodInsnNode) insn).desc = obf.mapMethodDesc(((MethodInsnNode) insn).desc);
            if (insn instanceof FrameNode && ((FrameNode) insn).stack != null) ((FrameNode) insn).stack.replaceAll(t -> t instanceof String ? obf.map((String) t) : t);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        jar.put(entry, writer.toByteArray());
    }

    private static InsnList call(String hook, String hookDesc, int... locals) {
        InsnList list = new InsnList();
        for (int local : locals) list.add(new VarInsnNode(Opcodes.ALOAD, local));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, hookDesc, false));
        return list;
    }
}
