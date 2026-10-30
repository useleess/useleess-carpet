package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Messages;
import com.useleess.carpet.core.Patch;
import com.useleess.carpet.core.Problem;
import com.useleess.carpet.core.VersionInfo;

import java.io.File;
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
 * The Useleess Carpet jar: run it like a normal server jar ({@code java -jar useleess-carpet.jar nogui}). It makes
 * sure the finished server jar sits in the server folder (built on this PC when missing or outdated, see
 * {@link Installer}), deletes itself when it lies in that folder ({@link SelfRemover}: from then on the server jar
 * is started directly) and starts the server in the same Java process, passing all arguments on. {@code --install-only [file]}
 * only builds it (moved to {@code file} when given), for hosts, CarpetSP or other tools.
 */
public final class Main {
    static final String PATCH = "/useleess-carpet/server.patch";
    static final String INSTALL_ONLY = "--install-only";

    private Main() {}

    public static void main(String[] args) throws Throwable {
        Messages m = Messages.load();
        if (Boolean.getBoolean("useleess.carpet.gui")
                || args.length == 0 && System.console() == null && !java.awt.GraphicsEnvironment.isHeadless()) {
            gui(m);
            return;
        }
        Downloader.Log log = (key, a) -> System.out.println(m.get("log.prefix") + m.get(key, a));
        Path built;
        VersionInfo v;
        try {
            Patch patch = readPatch();
            v = VersionInfo.load(patch.minecraft);
            log.say("start", buildVersion(), v.minecraft);
            Path folder = Paths.get("").toAbsolutePath();
            built = Installer.ensure(patch, v, folder, Downloader.HTTP, log);
            String java = JavaFinder.currentIs8() ? JavaFinder.current().toString() : "java";
            if (args.length > 0 && args[0].equals(INSTALL_ONLY)) {
                Path out = args.length > 1 ? Paths.get(args[1]).toAbsolutePath() : built;
                if (!out.equals(built)) Files.move(built, out, StandardCopyOption.REPLACE_EXISTING);
                log.say("install.written", out);
                if (folder.equals(out.getParent())) {
                    Path ours = scripts(folder, out, java, log);
                    SelfRemover.remove(SelfRemover.selfJar(), folder, out, log, "install.next_start", ours.getFileName());
                } else {
                    SelfRemover.remove(SelfRemover.selfJar(), folder, out, log, "install.next_start", out.getFileName());
                }
                return;
            }
            Path ours = scripts(folder, built, java, log);
            SelfRemover.remove(SelfRemover.selfJar(), folder, built, log, "install.next_start", ours.getFileName());
            log.say("starting", built.getFileName());
        } catch (Problem p) {
            System.err.println(m.get("log.prefix") + m.get(p));
            System.exit(1);
            return;
        }
        run(built, v.mainClass, args);
    }

    /**
     * Our own start script is (re)written; the owner's scripts are never changed (user 2026-10-30), the player is
     * only told which of them still start another jar. Returns our script.
     */
    static Path scripts(Path folder, Path serverJar, String java, Downloader.Log log) throws IOException {
        String name = serverJar.getFileName().toString();
        Path ours = StartScripts.create(folder, java, name, JavaFinder.isWindows());
        log.say("install.script_created", ours.getFileName());
        for (StartScripts.Script s : StartScripts.find(folder)) {
            if (!s.starts(name)) log.say("install.script_other", s.file.getFileName(), s.jar, ours.getFileName());
        }
        return ours;
    }

    /**
     * Double-click start: the installer window builds the server jar, finds a Java 8, writes our start script
     * (the owner's own are never changed), asks for the EULA, starts the server in Minecraft's own server window as
     * a separate program, then shows in a highlighted box which script to double-click from now on.
     */
    static void gui(Messages m) throws Exception {
        Window w = new Window(m);
        Downloader.Log log = w.log();
        try {
            Patch patch = readPatch();
            VersionInfo v = VersionInfo.load(patch.minecraft);
            log.say("start", buildVersion(), v.minecraft);
            Path folder = Paths.get("").toAbsolutePath();
            Path built = Installer.ensure(patch, v, folder, Downloader.HTTP, log);
            Path java = JavaFinder.findJava8();
            Path script = scripts(folder, built, java == null ? "java" : java.toString(), log);
            if (java == null) {
                log.say("gui.no_java8", "https://adoptium.net/temurin/releases/?version=8");
                w.finish(false);
                return;
            }
            log.say("gui.java_found", java);
            if (!Eula.accepted(folder)) {
                if (!w.ask("eula", "gui.agree", "gui.disagree", "gui.ask_eula", Eula.URL)) {
                    log.say("gui.eula_declined");
                    w.finish(false);
                    return;
                }
                Eula.accept(folder);
                log.say("gui.eula_accepted");
            }
            File nul = new File(JavaFinder.isWindows() ? "NUL" : "/dev/null");
            new ProcessBuilder(JavaFinder.windowless(java).toString(), "-Xmx2G", "-jar", built.toAbsolutePath().toString())
                    .directory(folder.toFile()).redirectInput(nul).redirectOutput(nul).redirectError(nul).start();
            log.say("gui.server_started");
            SelfRemover.remove(SelfRemover.selfJar(), folder, built, log, null, null);
            w.highlight(m.get("gui.next_start", script.getFileName()));
            w.finish(true);
        } catch (Problem p) {
            w.line(m.get(p));
            w.finish(false);
        } catch (IOException e) {
            w.line(m.get("gui.failed", e.toString()));
            w.finish(false);
        }
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
