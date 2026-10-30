package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Deletes the launcher ("installer") jar once the server jar is built (user 2026-10-30: it is wasted space; from then
 * on the server jar is started directly). Only a launcher that sits in the server folder itself is removed. Windows
 * keeps a running jar locked, so there it is deleted right after this Java process ends (a small hidden helper
 * waits for the lock to go); elsewhere at once.
 */
final class SelfRemover {
    private SelfRemover() {}

    /** The jar this launcher runs from, or null (started from classes, e.g. in tests). */
    static Path selfJar() {
        try {
            Path p = Paths.get(SelfRemover.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) && p.toString().endsWith(".jar") ? p : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** Removes {@code self} when it lies in {@code folder}; tells the player how to start the server from now on. */
    static void remove(Path self, Path folder, Path serverJar, Downloader.Log log) {
        if (self == null || self.equals(serverJar) || !folder.equals(self.toAbsolutePath().getParent())) return;
        try {
            Files.delete(self);
            log.say("install.installer_removed", self.getFileName());
        } catch (IOException locked) {
            deleteAfterExit(self);
            log.say("install.installer_removed_later", self.getFileName());
        }
        log.say("install.next_start", serverJar.getFileName());
    }

    private static void deleteAfterExit(Path self) {
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            self.toFile().deleteOnExit();
            return;
        }
        String file = self.toAbsolutePath().toString();
        // up to ~30 s: try, then wait a second (the lock goes when this Java process is gone)
        String loop = "for /L %i in (1,1,30) do @(if exist \"" + file + "\" (del /f /q \"" + file + "\" >nul 2>&1 & ping -n 2 127.0.0.1 >nul))";
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                File nul = new File("NUL");
                new ProcessBuilder("cmd.exe", "/c", loop).redirectInput(nul).redirectOutput(nul).redirectError(nul).start();
            } catch (IOException ignored) {
                // the launcher stays; the next start tries again
            }
        }));
    }
}
