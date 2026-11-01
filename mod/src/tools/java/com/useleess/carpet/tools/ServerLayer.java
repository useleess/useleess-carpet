package com.useleess.carpet.tools;

import com.redstonescope.carpetbuild.NameBridge;
import com.useleess.carpet.core.Patch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
        hook(jar, names, "carpet/CarpetServer", "onServerLoaded", SERVER_DESC, false, 0, "serverLoaded", SERVER_DESC);
        hook(jar, names, "carpet/CarpetServer", "tick", SERVER_DESC, true, 0, "tick", SERVER_DESC);
        hook(jar, names, "carpet/CarpetServer", "playerConnected", PLAYER_DESC, true, 0, "playerJoined", PLAYER_DESC);
        // PlayerList.playerLoggedOut (the player is its first argument): before the player data is written
        hook(jar, names, PLAYER_LIST, "func_72367_e", PLAYER_DESC, true, 1, "playerLeft", PLAYER_DESC);
        hook(jar, names, SERVER, "func_71260_j", "()V", true, 0, "serverStopping", SERVER_DESC);
        hook(jar, names, PLAYER, "func_70645_a", "(Lnet/minecraft/util/DamageSource;)V", true, 0, "playerDying", PLAYER_DESC);
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
     * Calls {@code Hooks.<hook>(local)} at a method's start or before each of its returns. The class is turned
     * into SRG names for the patch and back; the Carpet and Minecraft classes are not link-checked (they are the originals plus one call).
     */
    private static void hook(Map<String, byte[]> jar, NameBridge names, String owner, String method, String desc, boolean atStart, int local, String hook, String hookDesc) {
        String entry = names.obfClass(owner) + ".class";
        byte[] original = jar.get(entry);
        if (original == null) throw new IllegalStateException("the jar has no " + entry);
        ClassNode type = new ClassNode();
        new ClassReader(names.toSrg(original)).accept(type, 0);
        MethodNode target = type.methods.stream().filter(m -> m.name.equals(method) && m.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException(owner + "." + method + desc + " not found in the jar"));
        if (atStart) {
            target.instructions.insert(call(local, hook, hookDesc));
        } else {
            for (AbstractInsnNode insn : target.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.RETURN) target.instructions.insertBefore(insn, call(local, hook, hookDesc));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        jar.put(entry, names.toObf(writer.toByteArray()));
    }

    private static InsnList call(int local, String hook, String hookDesc) {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, local));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, hookDesc, false));
        return list;
    }
}
