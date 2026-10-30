package com.useleess.carpet.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The server folder's start scripts (user 2026-10-30): an owner's own script (start.bat, run.sh, ...) is never
 * replaced; the installer only offers to change the jar it starts, after saving a backup, and nothing else in it
 * (their Java, memory and flags stay). With no script at all, it writes {@value #NAME_WINDOWS} / {@value #NAME_OTHER}.
 */
final class StartScripts {
    static final String NAME_WINDOWS = "start-server.bat";
    static final String NAME_OTHER = "start-server.sh";
    /** "-jar name.jar" or "-jar "some name.jar"". */
    private static final Pattern JAR = Pattern.compile("-jar\\s+(\"([^\"]+\\.jar)\"|(\\S+\\.jar))", Pattern.CASE_INSENSITIVE);

    /** A script in the server folder and the jar it starts. */
    static final class Script {
        final Path file;
        final String jar;

        Script(Path file, String jar) {
            this.file = file;
            this.jar = jar;
        }

        boolean starts(String jarName) {
            return Paths.name(jar).equalsIgnoreCase(jarName);
        }
    }

    private StartScripts() {}

    static List<Script> find(Path folder) throws IOException {
        List<Script> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String n = p.getFileName().toString().toLowerCase();
                if (!Files.isRegularFile(p) || !(n.endsWith(".bat") || n.endsWith(".cmd") || n.endsWith(".sh"))) continue;
                Matcher m = JAR.matcher(read(p));
                if (m.find()) out.add(new Script(p, m.group(2) != null ? m.group(2) : m.group(3)));
            }
        }
        return out;
    }

    /** Points the script at {@code jarName}; the original is kept as {@code <name>.backup} (or .backup2, ...). */
    static Path update(Script s, String jarName) throws IOException {
        Path backup = s.file.resolveSibling(s.file.getFileName() + ".backup");
        for (int i = 2; Files.exists(backup); i++) backup = s.file.resolveSibling(s.file.getFileName() + ".backup" + i);
        Files.copy(s.file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        Matcher m = JAR.matcher(read(s.file));
        StringBuffer text = new StringBuffer();
        while (m.find()) {
            String old = m.group(2) != null ? m.group(2) : m.group(3);
            if (!old.equals(s.jar)) continue;
            String quote = m.group(2) != null ? "\"" : "";
            m.appendReplacement(text, Matcher.quoteReplacement("-jar " + quote + jarName + quote));
        }
        m.appendTail(text);
        Files.write(s.file, text.toString().getBytes(StandardCharsets.UTF_8));
        return backup;
    }

    /** Writes the start script for a folder that has none: Java (quoted path or "java"), 2 GB, server console. */
    static Path create(Path folder, String java, String jarName, boolean windows) throws IOException {
        String javaCmd = java.contains(" ") || java.contains("\\") || java.contains("/") ? "\"" + java + "\"" : java;
        String run = javaCmd + " -Xmx2G -jar " + jarName + " nogui";
        if (windows) {
            Path p = folder.resolve(NAME_WINDOWS);
            Files.write(p, ("@echo off\r\ncd /d \"%~dp0\"\r\n" + run + "\r\npause\r\n").getBytes(StandardCharsets.UTF_8));
            return p;
        }
        Path p = folder.resolve(NAME_OTHER);
        Files.write(p, ("#!/bin/sh\ncd \"$(dirname \"$0\")\"\nexec " + run + "\n").getBytes(StandardCharsets.UTF_8));
        p.toFile().setExecutable(true);
        return p;
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** File name part of a path written in a script ("C:\srv\x.jar" -> "x.jar"). */
    static final class Paths {
        private Paths() {}

        static String name(String path) {
            int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return path.substring(cut + 1);
        }
    }
}
