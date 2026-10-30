package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Messages;
import com.useleess.carpet.core.Patch;
import com.useleess.carpet.core.Problem;
import com.useleess.carpet.core.VersionInfo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;

/**
 * The Useleess Carpet jar: run it like a normal server jar ({@code java -jar useleess-carpet.jar nogui}). It builds
 * the finished server jar on this PC (see {@link Installer}) and starts it in the same Java process, passing all
 * arguments on. {@code --install-only [file]} only writes the finished jar (for hosts, CarpetSP or other tools).
 */
public final class Main {
    static final String PATCH = "/useleess-carpet/server.patch";
    static final String INSTALL_ONLY = "--install-only";

    private Main() {}

    public static void main(String[] args) throws Throwable {
        Messages m = Messages.load();
        Downloader.Log log = (key, a) -> System.out.println(m.get("log.prefix") + m.get(key, a));
        Path built;
        Patch patch;
        VersionInfo v;
        String version = buildVersion();
        try {
            patch = readPatch();
            v = VersionInfo.load(patch.minecraft);
            log.say("start", version, v.minecraft);
            built = Installer.ensure(patch, v, version, Paths.get("").toAbsolutePath(), Downloader.HTTP, log);
            if (args.length > 0 && args[0].equals(INSTALL_ONLY)) {
                Path out = Paths.get(args.length > 1 ? args[1] : "useleess-carpet-" + version + "-" + v.minecraft + "-server.jar");
                Files.copy(built, out, StandardCopyOption.REPLACE_EXISTING);
                log.say("install.written", out.toAbsolutePath());
                return;
            }
        } catch (Problem p) {
            System.err.println(m.get("log.prefix") + m.get(p));
            System.exit(1);
            return;
        }
        run(built, v.mainClass, args);
    }

    /** Starts the finished server jar's main class in this process, its own classes only (not ours). */
    static void run(Path jar, String mainClass, String[] args) throws Throwable {
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getSystemClassLoader().getParent());
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Class.forName(mainClass, true, loader).getMethod("main", String[].class).invoke(null, (Object) Arrays.copyOf(args, args.length));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    static Patch readPatch() throws IOException {
        try (InputStream in = Main.class.getResourceAsStream(PATCH)) {
            if (in == null) throw new Problem("error.broken_patch");
            return Patch.read(in);
        }
    }

    static String buildVersion() {
        Properties p = new Properties();
        try (InputStream in = Main.class.getResourceAsStream("/useleess-carpet/build.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // no build info: "dev"
        }
        return p.getProperty("version", "dev");
    }
}
