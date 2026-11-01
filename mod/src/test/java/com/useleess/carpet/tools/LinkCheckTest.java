package com.useleess.carpet.tools;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LinkCheck: a class of ours must give a body to every abstract method it inherits from the server jar. */
class LinkCheckTest {
    /** A server interface "r" with one abstract method (on the server; Forge's compile jar had a default body). */
    private static Map<String, byte[]> jarWithRecipeInterface() {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "r", null, "java/lang/Object", null);
        w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "b", "()I", null, null).visitEnd();
        w.visitEnd();
        Map<String, byte[]> jar = new HashMap<>();
        jar.put("r.class", w.toByteArray());
        return jar;
    }

    private static byte[] ours(boolean withBody) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "x/Ours", null, "java/lang/Object", new String[]{"r"});
        if (withBody) {
            MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "b", "()I", null, null);
            m.visitCode();
            m.visitInsn(Opcodes.ICONST_0);
            m.visitInsn(Opcodes.IRETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        w.visitEnd();
        return w.toByteArray();
    }

    @Test void aMissingInterfaceMethodFailsTheCheck() {
        Map<String, byte[]> jar = jarWithRecipeInterface();
        jar.put("x/Ours.class", ours(false));
        TreeSet<String> problems = new LinkCheck(jar).check(Collections.singletonList("x/Ours.class"));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.first().contains("x/Ours does not implement b()I"), problems.toString());
    }

    @Test void anImplementedMethodPasses() {
        Map<String, byte[]> jar = jarWithRecipeInterface();
        jar.put("x/Ours.class", ours(true));
        assertEquals(new TreeSet<>(), new LinkCheck(jar).check(Collections.singletonList("x/Ours.class")));
    }
}
