package com.useleess.carpet.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Before the jar is written: every class, method and field the classes we add or patch use must exist in the jar (or
 * in Java itself), and every abstract method a class of ours inherits from the jar has a body. Catches names that did
 * not translate, calls into Forge-only parts of the compile jar and Forge's default methods that are abstract on the
 * server, which would otherwise only break when the server runs them.
 */
final class LinkCheck {
    private final Map<String, byte[]> jar;
    private final Map<String, ClassNode> known = new HashMap<>();
    private final TreeSet<String> problems = new TreeSet<>();

    LinkCheck(Map<String, byte[]> jar) {
        this.jar = jar;
    }

    /** The problems found in these classes (jar entry names); empty = every link holds. */
    TreeSet<String> check(Iterable<String> entries) {
        for (String entry : entries) {
            ClassNode type = new ClassNode();
            new ClassReader(jar.get(entry)).accept(type, ClassReader.SKIP_FRAMES);
            if (type.superName != null) node(type.superName);
            for (String i : type.interfaces) node(i);
            if ((type.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == 0) checkImplemented(type);
            for (MethodNode method : type.methods) {
                for (AbstractInsnNode insn : method.instructions.toArray()) check(type.name + "." + method.name, insn);
            }
        }
        return problems;
    }

    private void check(String where, AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) insn;
            if (!m.owner.startsWith("[") && !hasMethod(m.owner, m.name, m.desc)) problems.add(where + " uses " + m.owner + "." + m.name + m.desc);
        } else if (insn instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) insn;
            if (!hasField(f.owner, f.name)) problems.add(where + " uses field " + f.owner + "." + f.name);
        } else if (insn instanceof TypeInsnNode) {
            String t = ((TypeInsnNode) insn).desc;
            if (!t.startsWith("[")) node(t);
        } else if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Type) {
            Type t = (Type) ((LdcInsnNode) insn).cst;
            if (t.getSort() == Type.OBJECT) node(t.getInternalName());
        }
    }

    private void checkImplemented(ClassNode type) {
        Set<String> wanted = new HashSet<>();
        abstractMethods(type.name, wanted, new HashSet<>());
        for (String m : wanted) {
            int split = m.indexOf('(');
            if (!implemented(type.name, m.substring(0, split), m.substring(split))) problems.add(type.name + " does not implement " + m);
        }
    }

    /** name + desc of the abstract methods declared in a class of the jar and its parents. */
    private void abstractMethods(String name, Set<String> out, Set<String> seen) {
        ClassNode type = jar.containsKey(name + ".class") && seen.add(name) ? node(name) : null;
        if (type == null) return;
        for (MethodNode m : type.methods) if ((m.access & Opcodes.ACC_ABSTRACT) != 0) out.add(m.name + m.desc);
        if (type.superName != null) abstractMethods(type.superName, out, seen);
        for (String i : type.interfaces) abstractMethods(i, out, seen);
    }

    /** Whether the class or one of its parents in the jar has a body for that method. */
    private boolean implemented(String name, String method, String desc) {
        ClassNode type = jar.containsKey(name + ".class") ? node(name) : null;
        if (type == null) return false;
        for (MethodNode m : type.methods) if (m.name.equals(method) && m.desc.equals(desc) && (m.access & Opcodes.ACC_ABSTRACT) == 0) return true;
        if (type.superName != null && implemented(type.superName, method, desc)) return true;
        for (String i : type.interfaces) if (implemented(i, method, desc)) return true;
        return false;
    }

    private boolean hasMethod(String owner, String name, String desc) {
        ClassNode type = node(owner);
        if (type == null) return false;
        if (!jar.containsKey(owner + ".class")) return javaHasMethod(owner, name, desc);
        for (MethodNode m : type.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true;
        if (type.superName != null && hasMethod(type.superName, name, desc)) return true;
        for (String i : type.interfaces) if (hasMethod(i, name, desc)) return true;
        return false;
    }

    private boolean hasField(String owner, String name) {
        ClassNode type = node(owner);
        if (type == null) return false;
        if (!jar.containsKey(owner + ".class")) return javaHasField(owner, name);
        for (FieldNode f : type.fields) if (f.name.equals(name)) return true;
        if (type.superName != null && hasField(type.superName, name)) return true;
        for (String i : type.interfaces) if (hasField(i, name)) return true;
        return false;
    }

    private boolean javaHasMethod(String owner, String name, String desc) {
        if (name.equals("<init>")) return true;
        try {
            Class<?> c = Class.forName(owner.replace('/', '.'), false, ClassLoader.getSystemClassLoader());
            for (Method m : c.getMethods()) if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return true;
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return true;
            }
            return c.isInterface() && hasObjectMethod(name, desc);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean javaHasField(String owner, String name) {
        try {
            Class<?> c = Class.forName(owner.replace('/', '.'), false, ClassLoader.getSystemClassLoader());
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) if (f.getName().equals(name)) return true;
            }
            for (Class<?> i : c.getInterfaces()) if (javaHasField(i.getName().replace('.', '/'), name)) return true;
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean hasObjectMethod(String name, String desc) {
        for (Method m : Object.class.getMethods()) if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return true;
        return false;
    }

    /** A class from the jar, or a stand-in for one of Java's own; null (and a problem) when neither has it. */
    private ClassNode node(String name) {
        if (known.containsKey(name)) return known.get(name);
        ClassNode type = null;
        byte[] bytes = jar.get(name + ".class");
        if (bytes != null) {
            type = new ClassNode();
            new ClassReader(bytes).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.EXPAND_FRAMES);
        } else if (name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("sun/")) {
            type = new ClassNode();
            type.name = name;
        } else {
            problems.add("missing class " + name);
        }
        known.put(name, type);
        return type;
    }
}
