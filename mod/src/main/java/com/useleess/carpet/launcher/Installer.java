package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Patch;
import com.useleess.carpet.core.VersionInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Makes the finished server jar on the player's own PC: Mojang's official jar + our patch, kept in the
 * {@value #CACHE} folder next to the server so later starts skip straight to running. A cached jar is reused
 * only while its recorded fingerprint matches the patch (a new launcher version rebuilds it).
 */
public final class Installer {
    public static final String CACHE = "useleess-carpet";

    private Installer() {}

    public static Path ensure(Patch patch, VersionInfo v, String version, Path folder, Downloader web, Downloader.Log log)
            throws IOException {
        Path cache = folder.resolve(CACHE);
        Files.createDirectories(cache);
        Path built = cache.resolve("useleess-carpet-" + version + "-" + v.minecraft + "-server.jar");
        Path marker = cache.resolve(built.getFileName() + ".ok");
        String digest = patch.digest();
        if (Files.isRegularFile(built) && Files.isRegularFile(marker)
                && digest.equals(new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim())) {
            log.say("install.ready_cached");
            return built;
        }
        Path official = cache.resolve("mojang-server-" + v.minecraft + ".jar");
        Downloader.officialJar(v, folder, official, web, log);
        log.say("install.building");
        Files.deleteIfExists(marker);
        Path part = cache.resolve(built.getFileName() + ".part");
        try {
            patch.apply(official, part);
            Files.move(part, built, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
        Files.write(marker, digest.getBytes(StandardCharsets.UTF_8));
        // older builds of the finished jar are no longer needed
        try (java.util.stream.Stream<Path> old = Files.list(cache)) {
            for (Path p : (Iterable<Path>) old::iterator) {
                String n = p.getFileName().toString();
                if (n.startsWith("useleess-carpet-") && !n.startsWith(built.getFileName().toString())) Files.deleteIfExists(p);
            }
        }
        log.say("install.built");
        return built;
    }
}
