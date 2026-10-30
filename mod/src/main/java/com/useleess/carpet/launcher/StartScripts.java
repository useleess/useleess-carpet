package com.useleess.carpet.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Start scripts (user 2026-10-30): the installer always writes its OWN script, {@value #NAME_WINDOWS} /
 * {@value #NAME_OTHER}, and never changes an owner's script (start.bat, run.sh, ...); those are only found so the
 * player can be told they still start the old jar.
 */
final class StartScripts {
    static final String NAME_WINDOWS = "start-useleess-carpet.bat";
    static final String NAME_OTHER = "start-useleess-carpet.sh";
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
            int cut = Math.max(jar.lastIndexOf('/'), jar.lastIndexOf('\\'));
            return jar.substring(cut + 1).equalsIgnoreCase(jarName);
        }
    }

    private StartScripts() {}

    /** The owner's scripts that start some jar (ours left out). */
    static List<Script> find(Path folder) throws IOException {
        List<Script> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String n = p.getFileName().toString().toLowerCase();
                if (!Files.isRegularFile(p) || n.equals(NAME_WINDOWS) || n.equals(NAME_OTHER)) continue;
                if (!(n.endsWith(".bat") || n.endsWith(".cmd") || n.endsWith(".sh"))) continue;
                Matcher m = JAR.matcher(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                if (m.find()) out.add(new Script(p, m.group(2) != null ? m.group(2) : m.group(3)));
            }
        }
        return out;
    }

    /** Writes (or rewrites) our own start script: Java (quoted path or "java"), 2 GB, the server console. */
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
}
