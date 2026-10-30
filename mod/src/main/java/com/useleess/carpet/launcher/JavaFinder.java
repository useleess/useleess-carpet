package com.useleess.carpet.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds a Java 8 on this PC (a 1.12.2 server needs it; double-clicking a jar often uses a newer default Java): this
 * Java when it is 8, JAVA_HOME, the usual install folders, and the Java 8 the Minecraft launcher brings along
 * ("jre-legacy"). Each candidate is asked for its version, so a folder name never decides.
 */
final class JavaFinder {
    private static final Pattern VERSION = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?");
    private static final String[] VENDORS = {"Java", "Eclipse Adoptium", "Eclipse Foundation", "AdoptOpenJDK", "Zulu",
            "Amazon Corretto", "BellSoft", "Microsoft", "Semeru"};
    private static final String LEGACY = "runtime/jre-legacy/windows-x64/jre-legacy/bin/java.exe";

    private JavaFinder() {}

    /** The Java major version in a "java -version" answer ("1.8.0_452" -> 8, "21.0.2" -> 21), or 0. */
    static int major(String versionOutput) {
        Matcher m = VERSION.matcher(versionOutput);
        if (!m.find()) return 0;
        int first = Integer.parseInt(m.group(1));
        return first == 1 && m.group(2) != null ? Integer.parseInt(m.group(2)) : first;
    }

    /** The java program of this Java (java.exe on Windows). */
    static Path current() {
        return javaIn(Paths.get(System.getProperty("java.home")), isWindows());
    }

    static boolean currentIs8() {
        String v = System.getProperty("java.specification.version", "");
        return v.equals("1.8") || v.equals("8");
    }

    static Path findJava8() {
        if (currentIs8()) return current();
        for (Path p : candidates(System.getenv(), isWindows())) {
            if (Files.isRegularFile(p) && major(versionOf(p)) == 8) return p;
        }
        return null;
    }

    /** Where a Java may be, in order (only existing files matter). */
    static List<Path> candidates(Map<String, String> env, boolean windows) {
        Set<Path> out = new LinkedHashSet<>();
        if (env.get("JAVA_HOME") != null) out.add(javaIn(Paths.get(env.get("JAVA_HOME")), windows));
        if (windows) {
            for (String root : new String[]{env.get("ProgramFiles"), env.get("ProgramFiles(x86)"), env.get("ProgramW6432")}) {
                if (root == null) continue;
                for (String vendor : VENDORS) out.addAll(children(Paths.get(root, vendor), windows));
            }
            if (env.get("APPDATA") != null) out.add(Paths.get(env.get("APPDATA"), ".minecraft", LEGACY));
            if (env.get("LOCALAPPDATA") != null) {
                out.add(Paths.get(env.get("LOCALAPPDATA"), "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe",
                        "LocalCache", "Local", LEGACY));
            }
            if (env.get("ProgramFiles(x86)") != null) out.add(Paths.get(env.get("ProgramFiles(x86)"), "Minecraft Launcher", LEGACY));
        } else {
            out.addAll(children(Paths.get("/usr/lib/jvm"), false));
            for (Path home : children(Paths.get("/Library/Java/JavaVirtualMachines"), false)) {
                out.add(home.getParent().getParent().resolve("Contents/Home/bin/java"));
            }
        }
        if (env.get("PATH") != null) {
            for (String dir : env.get("PATH").split(File.pathSeparator)) {
                if (!dir.trim().isEmpty()) out.add(Paths.get(dir.trim(), windows ? "java.exe" : "java"));
            }
        }
        return new ArrayList<>(out);
    }

    private static List<Path> children(Path dir, boolean windows) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.sorted().forEach(home -> out.add(javaIn(home, windows)));
        } catch (IOException ignored) {
            // unreadable folder: nothing there for us
        }
        return out;
    }

    private static Path javaIn(Path home, boolean windows) {
        return home.resolve("bin").resolve(windows ? "java.exe" : "java");
    }

    static String versionOf(Path java) {
        try {
            Process p = new ProcessBuilder(java.toString(), "-version").redirectErrorStream(true).start();
            p.getOutputStream().close();
            byte[] out;
            try (InputStream in = p.getInputStream()) {
                out = com.useleess.carpet.core.Patch.readFully(in);
            }
            p.waitFor(10, TimeUnit.SECONDS);
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            return "";
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    /** javaw next to java on Windows (no extra console window), else java itself. */
    static Path windowless(Path java) {
        Path w = java.resolveSibling("javaw.exe");
        return Files.isRegularFile(w) ? w : java;
    }
}
