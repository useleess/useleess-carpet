package com.useleess.carpet;

import com.useleess.carpet.core.Messages;
import com.useleess.carpet.core.VersionInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * House rules, checked on every build: everything the launcher says comes from the language file (no English in
 * code, every key used exists and every key in the file is used), the per-version data is complete, and no block
 * of code is copied between files (one shared core, never copies).
 */
class HygieneTest {
    static final Path MAIN = Paths.get("src/main/java");
    static final Path TOOLS = Paths.get("src/tools/java");
    static final Pattern KEY = Pattern.compile("\"((?:log|install|error|gui)\\.[a-z0-9_.]+|start|starting)\"");

    static List<Path> sources(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test void everyMessageKeyExistsAndIsUsed() throws IOException {
        Map<String, String> english = Messages.read("en_us");
        assertFalse(english.isEmpty());
        TreeSet<String> used = new TreeSet<>();
        for (Path p : sources(MAIN)) {
            Matcher m = KEY.matcher(read(p));
            while (m.find()) used.add(m.group(1));
        }
        TreeSet<String> missing = new TreeSet<>(used);
        missing.removeAll(english.keySet());
        assertTrue(missing.isEmpty(), "keys used in code but not in en_us.json: " + missing);
        TreeSet<String> unused = new TreeSet<>(english.keySet());
        unused.removeAll(used);
        assertTrue(unused.isEmpty(), "keys in en_us.json never used: " + unused);
    }

    @Test void noPlayerTextInLauncherCode() throws IOException {
        Pattern printLiteral = Pattern.compile("System\\.(out|err)\\.print(ln)?\\(\\s*\"");
        Pattern problemText = Pattern.compile("new Problem\\((\\w+, )?\"(?!(log|install|error|gui)\\.)");
        for (Path p : sources(MAIN)) {
            String s = read(p);
            assertFalse(printLiteral.matcher(s).find(), p + " prints English text: use Messages");
            assertFalse(problemText.matcher(s).find(), p + " has a Problem without a message key");
        }
    }

    @Test void messagesAreFormattedWithTheirValues() {
        Messages m = Messages.load();
        assertEquals("[Useleess Carpet] ", m.get("log.prefix"));
        assertTrue(m.get("start", "9.9.9", "1.12.2").contains("9.9.9"));
        assertEquals("no.such.key", m.get("no.such.key"));
    }

    @Test void versionDataIsComplete() throws IOException {
        VersionInfo v = VersionInfo.load("1.12.2");
        assertTrue(v.serverSha1.matches("[0-9a-f]{40}"));
        assertFalse(v.serverUrls.isEmpty());
        for (String u : v.serverUrls) {
            assertTrue(u.startsWith("https://") && (u.contains(".mojang.com/")), "only Mojang's own servers: " + u);
            assertTrue(u.contains(v.serverSha1), "the URL names the exact file: " + u);
        }
        assertEquals("net.minecraft.server.MinecraftServer", v.mainClass);
        assertThrows(IOException.class, () -> VersionInfo.load("0.0.0"));
    }

    @Test void noCopiedCodeBlocks() throws IOException {
        int block = 6;
        Map<String, String> seen = new HashMap<>();
        List<String> copies = new ArrayList<>();
        List<Path> all = new ArrayList<>(sources(MAIN));
        all.addAll(sources(TOOLS));
        for (Path p : all) {
            List<String> lines = new ArrayList<>();
            for (String l : read(p).split("\n")) {
                String t = l.trim();
                if (t.length() > 3 && !t.startsWith("import ") && !t.startsWith("*") && !t.startsWith("/") && !t.equals("}") && !t.equals("});")) lines.add(t);
            }
            for (int i = 0; i + block <= lines.size(); i++) {
                String key = String.join("\n", lines.subList(i, i + block));
                String where = p + ":" + i;
                String before = seen.putIfAbsent(key, where);
                if (before != null && !before.startsWith(p + ":")) copies.add(before + " = " + where);
            }
        }
        assertTrue(copies.isEmpty(), "copied code (move it into one shared place): " + copies);
    }
}
