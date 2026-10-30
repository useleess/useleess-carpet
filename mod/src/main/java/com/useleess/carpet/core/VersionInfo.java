package com.useleess.carpet.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * The per-Minecraft-version facts (the only version-specific part): where Mojang's official server jar is, its
 * SHA-1, the file names a server owner may already have it under, and the server's main class. One file per
 * version in {@code useleess-carpet/minecraft/<version>.properties}.
 */
public final class VersionInfo {
    public final String minecraft;
    public final String serverSha1;
    public final List<String> serverUrls;
    public final List<String> localNames;
    public final String mainClass;

    public VersionInfo(String minecraft, String serverSha1, List<String> serverUrls, List<String> localNames, String mainClass) {
        this.minecraft = minecraft;
        this.serverSha1 = serverSha1;
        this.serverUrls = serverUrls;
        this.localNames = localNames;
        this.mainClass = mainClass;
    }

    public static VersionInfo load(String minecraft) throws Problem {
        Properties p = new Properties();
        try (InputStream in = VersionInfo.class.getResourceAsStream("/useleess-carpet/minecraft/" + minecraft + ".properties")) {
            if (in == null) throw new Problem("error.unknown_version", minecraft);
            p.load(in);
        } catch (Problem e) {
            throw e;
        } catch (IOException e) {
            throw new Problem(e, "error.unknown_version", minecraft);
        }
        return new VersionInfo(minecraft, p.getProperty("serverSha1"), list(p, "serverUrls"), list(p, "localNames"),
                p.getProperty("mainClass"));
    }

    private static List<String> list(Properties p, String key) {
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList(p.getProperty(key, "").split(","))) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }
}
