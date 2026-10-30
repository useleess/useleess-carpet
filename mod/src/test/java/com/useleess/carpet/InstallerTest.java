package com.useleess.carpet;

import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Hashes;
import com.useleess.carpet.core.Patch;
import com.useleess.carpet.core.Problem;
import com.useleess.carpet.core.VersionInfo;
import com.useleess.carpet.launcher.Installer;
import com.useleess.carpet.tools.PatchMaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The launcher's install step with a stand-in for the web (no game, no network). */
class InstallerTest {
    static final String SERVER = "useleess-carpet-1.12.2-server.jar";
    @TempDir Path dir;
    Path server;
    Path mojang;
    Path master;
    Patch patch;
    VersionInfo v;
    final List<String> fetched = new ArrayList<>();
    final List<String> said = new ArrayList<>();
    final Downloader.Log log = (key, args) -> said.add(key);

    @BeforeEach void setUp() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.class", PatchTest.random(1, 2000));
        files.put("b.class", PatchTest.random(2, 2000));
        mojang = PatchTest.jar(dir.resolve("mojang.jar"), files);
        Map<String, byte[]> m = new LinkedHashMap<>(files);
        m.put("carpet/C.class", PatchTest.random(3, 700));
        master = PatchTest.jar(dir.resolve("master.jar"), m);
        patch = PatchMaker.make("1.12.2", mojang, master);
        v = new VersionInfo("1.12.2", Hashes.sha1Hex(mojang), Arrays.asList("https://one.example/s.jar", "https://two.example/s.jar"),
                Arrays.asList("minecraft_server.1.12.2.jar"), "x.Main");
        server = Files.createDirectories(dir.resolve("server"));
    }

    Downloader serving(Path file) {
        return (url, to) -> {
            fetched.add(url);
            Files.copy(file, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        };
    }

    Downloader failing() {
        return (url, to) -> {
            fetched.add(url);
            throw new IOException("offline");
        };
    }

    Path ensure(Downloader web) throws IOException {
        return Installer.ensure(patch, v, server, web, log);
    }

    /** What the server folder holds (names, sub folders with a slash). */
    TreeSet<String> folder() throws IOException {
        try (Stream<Path> s = Files.list(server)) {
            return s.map(p -> p.getFileName() + (Files.isDirectory(p) ? "/" : "")).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    void assertIsMaster(Path built) throws IOException {
        Map<String, byte[]> got = Patch.readAll(built);
        Map<String, byte[]> want = Patch.readAll(master);
        got.remove(Installer.STAMP);
        assertEquals(new ArrayList<>(want.keySet()), new ArrayList<>(got.keySet()));
        for (String n : want.keySet()) assertArrayEquals(want.get(n), got.get(n), n);
    }

    @Test void downloadsFromMojangBuildsAndLeavesOnlyTheServerJar() throws IOException {
        Path built = ensure(serving(mojang));
        assertEquals(server.resolve(SERVER), built);
        assertIsMaster(built);
        assertEquals(Arrays.asList("https://one.example/s.jar"), fetched);
        assertTrue(said.contains("install.downloading") && said.contains("install.built"));
        assertEquals(new TreeSet<>(Arrays.asList(SERVER)), folder(), "no temp folder, no Mojang jar left");
    }

    @Test void secondStartReusesTheServerJar() throws IOException {
        Path built = ensure(serving(mojang));
        long time = Files.getLastModifiedTime(built).toMillis();
        said.clear();
        fetched.clear();
        assertEquals(built, ensure(failing()));
        assertEquals(time, Files.getLastModifiedTime(built).toMillis());
        assertTrue(fetched.isEmpty());
        assertEquals(Arrays.asList("install.ready_cached"), said);
    }

    @Test void usesTheOwnersCopyAndKeepsIt() throws IOException {
        Files.copy(mojang, server.resolve("minecraft_server.1.12.2.jar"));
        assertIsMaster(ensure(failing()));
        assertTrue(fetched.isEmpty());
        assertTrue(said.contains("install.using_local"));
        assertEquals(new TreeSet<>(Arrays.asList(SERVER, "minecraft_server.1.12.2.jar")), folder(), "the owner's file stays");
    }

    @Test void ignoresAnOwnersCopyWithTheWrongHash() throws IOException {
        Files.write(server.resolve("minecraft_server.1.12.2.jar"), new byte[]{1, 2, 3});
        assertIsMaster(ensure(serving(mojang)));
        assertEquals(1, fetched.size());
    }

    @Test void triesTheNextMirrorAndRejectsWrongDownloads() throws IOException {
        Path wrong = Files.write(dir.resolve("wrong.jar"), new byte[]{9});
        Downloader web = (url, to) -> {
            fetched.add(url);
            Files.copy(url.contains("one") ? wrong : mojang, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        };
        assertIsMaster(ensure(web));
        assertEquals(2, fetched.size());
    }

    @Test void noInternetGivesAClearProblemAndLeavesNothing() throws IOException {
        Problem p = assertThrows(Problem.class, () -> ensure(failing()));
        assertEquals("error.download_failed", p.key);
        assertEquals(2, fetched.size());
        assertTrue(folder().isEmpty(), "the temp folder is removed after a failure too: " + folder());
    }

    @Test void damagedServerJarIsRebuilt() throws IOException {
        Path built = ensure(serving(mojang));
        Files.write(built, new byte[]{1, 2, 3});
        said.clear();
        assertIsMaster(ensure(serving(mojang)));
        assertTrue(said.contains("install.building"));
    }

    @Test void serverJarFromAnotherPatchIsRebuilt() throws IOException {
        // another launcher version with a different patch
        Map<String, byte[]> m = Patch.readAll(master);
        m.put("carpet/D.class", PatchTest.random(4, 300));
        Patch other = PatchMaker.make("1.12.2", mojang, PatchTest.jar(dir.resolve("other.jar"), m));
        Installer.ensure(other, v, server, serving(mojang), log);
        said.clear();
        assertIsMaster(ensure(serving(mojang)));
        assertTrue(said.contains("install.building"));
        assertEquals(new TreeSet<>(Arrays.asList(SERVER)), folder());
    }

    @Test void oldCacheFolderFrom010IsRemoved() throws IOException {
        Path old = Files.createDirectories(server.resolve("useleess-carpet"));
        Files.write(old.resolve("mojang-server-1.12.2.jar"), new byte[]{1});
        Files.write(old.resolve("useleess-carpet-0.1.0-1.12.2-server.jar"), new byte[]{2});
        Files.write(old.resolve("useleess-carpet-0.1.0-1.12.2-server.jar.ok"), new byte[]{3});
        ensure(serving(mojang));
        assertEquals(new TreeSet<>(Arrays.asList(SERVER)), folder());
    }

    @Test void oldCacheFolderWithSomethingElseInsideIsKept() throws IOException {
        Path old = Files.createDirectories(server.resolve("useleess-carpet"));
        Files.write(old.resolve("mojang-server-1.12.2.jar"), new byte[]{1});
        Files.write(old.resolve("my-notes.txt"), new byte[]{2});
        ensure(serving(mojang));
        assertEquals(new TreeSet<>(Arrays.asList("my-notes.txt")), new TreeSet<>(Arrays.asList(old.toFile().list())));
    }
}
