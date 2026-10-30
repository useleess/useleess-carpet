package com.useleess.carpet.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Gets a file from the web into a path. An interface so tests can stand in for the network. */
public interface Downloader {
    void fetch(String url, Path to) throws IOException;

    Downloader HTTP = (url, to) -> {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "UseleessCarpet");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream()) {
            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
        }
    };

    /**
     * Mojang's official server jar for {@code v} at {@code target}: a copy the owner already has (checked by
     * SHA-1), otherwise downloaded from Mojang's own servers and checked. Returns whether it was downloaded.
     */
    static boolean officialJar(VersionInfo v, Path folder, Path target, Downloader web, Log log) throws IOException {
        if (Files.isRegularFile(target) && v.serverSha1.equals(Hashes.sha1Hex(target))) return false;
        for (String name : v.localNames) {
            Path own = folder.resolve(name);
            if (Files.isRegularFile(own) && v.serverSha1.equals(Hashes.sha1Hex(own))) {
                log.say("install.using_local", name);
                Files.copy(own, target, StandardCopyOption.REPLACE_EXISTING);
                return false;
            }
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        IOException last = null;
        for (String url : v.serverUrls) {
            log.say("install.downloading", v.minecraft, url);
            try {
                web.fetch(url, part);
                String got = Hashes.sha1Hex(part);
                if (v.serverSha1.equals(got)) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
                last = new Problem("error.wrong_vanilla", got, v.serverSha1);
            } catch (IOException e) {
                last = e;
            } finally {
                Files.deleteIfExists(part);
            }
        }
        throw new Problem(last, "error.download_failed", v.minecraft, last == null ? "" : last.getMessage());
    }

    /** Where the launcher's progress lines go. */
    interface Log {
        void say(String key, Object... args);
    }
}
