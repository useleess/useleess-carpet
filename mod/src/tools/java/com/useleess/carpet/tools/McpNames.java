package com.useleess.carpet.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Readable (MCP) names -> SRG names, for code written and compiled against MCP-named Minecraft: from MCP's
 * {@code srg_to_snapshot} table (class names are the same in both). A member is looked up on the class it is used on
 * and then up its parents (a call through a subclass names the subclass), the parents coming from the classes given.
 */
final class McpNames {
    private final Map<String, Map<String, String>> methods = new HashMap<>();
    private final Map<String, Map<String, String>> fields = new HashMap<>();
    private final Map<String, List<String>> parents = new HashMap<>();

    McpNames(Path srgToMcp, Map<String, byte[]> hierarchy) throws IOException {
        String owner = null;
        for (String line : Files.readAllLines(srgToMcp, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            String[] p = line.trim().split(" ");
            if (!line.startsWith("\t")) {
                owner = p[0];
            } else if (p.length == 3) {
                methods.computeIfAbsent(owner, k -> new HashMap<>()).put(p[2] + " " + p[1], p[0]);
            } else {
                fields.computeIfAbsent(owner, k -> new HashMap<>()).put(p[1], p[0]);
            }
        }
        for (Map.Entry<String, byte[]> e : hierarchy.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassReader r = new ClassReader(e.getValue());
            List<String> up = new ArrayList<>();
            if (r.getSuperName() != null) up.add(r.getSuperName());
            for (String i : r.getInterfaces()) up.add(i);
            parents.put(r.getClassName(), up);
        }
    }

    byte[] toSrg(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassRemapper(writer, new Remapper() {
            @Override public String mapMethodName(String owner, String name, String desc) {
                String srg = find(methods, owner, name + " " + desc);
                return srg == null ? name : srg;
            }

            @Override public String mapFieldName(String owner, String name, String desc) {
                String srg = find(fields, owner, name);
                return srg == null ? name : srg;
            }
        }), 0);
        return writer.toByteArray();
    }

    private String find(Map<String, Map<String, String>> table, String owner, String key) {
        Map<String, String> own = table.get(owner);
        if (own != null && own.containsKey(key)) return own.get(key);
        for (String parent : parents.getOrDefault(owner, new ArrayList<>())) {
            String found = find(table, parent, key);
            if (found != null) return found;
        }
        return null;
    }
}
