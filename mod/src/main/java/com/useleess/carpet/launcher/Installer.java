package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Patch;
import com.useleess.carpet.core.VersionInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Makes the finished server jar on the player's own PC and leaves the server folder tidy (user 2026-10-30: testers
 * were confused by a cache folder holding Mojang's jar and the built one). The result is ONE file in the server
 * folder, {@value #SERVER_JAR}; Mojang's jar is downloaded into a temporary folder that is deleted afterwards, also
 * when something fails. The built jar carries the patch's fingerprint ({@value #STAMP}), so a later start reuses
 * it, and a missing, damaged or older one is simply built again.
 */
public final class Installer {
    public static final String SERVER_JAR = "useleess-carpet-%s-server.jar";
    public static final String STAMP = "META-INF/useleess-carpet.stamp";
    static final String TEMP = ".useleess-carpet-temp";
    /** 0.1.0 kept Mojang's jar and the built one in this folder: removed (only the files it made). */
    static final String OLD_CACHE = "useleess-carpet";

    private Installer() {}

    public static Path serverJar(Path folder, VersionInfo v) {
        return folder.resolve(String.format(SERVER_JAR, v.minecraft));
    }

    public static Path ensure(Patch patch, VersionInfo v, Path folder, Downloader web, Downloader.Log log) throws IOException {
        removeOldCache(folder);
        Path server = serverJar(folder, v);
        String digest = patch.digest();
        if (digest.equals(stamp(server))) {
            log.say("install.ready_cached", server.getFileName());
            return server;
        }
        Path temp = folder.resolve(TEMP);
        try {
            deleteTree(temp);
            Files.createDirectories(temp);
            Path official = temp.resolve("mojang-server-" + v.minecraft + ".jar");
            Downloader.officialJar(v, folder, official, web, log);
            log.say("install.building");
            Path part = temp.resolve("server.jar");
            patch.apply(official, part, STAMP, digest);
            Files.move(part, server, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteTree(temp);
        }
        log.say("install.built", server.getFileName());
        return server;
    }

    /** The fingerprint inside a built server jar, or null (missing, damaged, not ours). */
    static String stamp(Path jar) {
        if (!Files.isRegularFile(jar)) return null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry(STAMP);
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                byte[] b = new byte[200];
                int n = in.read(b);
                return n <= 0 ? null : new String(b, 0, n, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static void removeOldCache(Path folder) throws IOException {
        Path old = folder.resolve(OLD_CACHE);
        if (!Files.isDirectory(old)) return;
        try (Stream<Path> files = Files.list(old)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString();
                if (Files.isRegularFile(p) && (n.startsWith("mojang-server-") || n.startsWith("useleess-carpet-"))) Files.delete(p);
            }
        }
        try (Stream<Path> rest = Files.list(old)) {
            if (!rest.findAny().isPresent()) Files.delete(old);
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> all = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) all.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
        }
    }
}
