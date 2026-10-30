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

import static org.junit.jupiter.api.Assertions.*;

/** The launcher's install step with a stand-in for the web (no game, no network). */
class InstallerTest {
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

    void assertIsMaster(Path built) throws IOException {
        Map<String, byte[]> got = Patch.readAll(built);
        Map<String, byte[]> want = Patch.readAll(master);
        assertEquals(want.keySet(), got.keySet());
        for (String n : want.keySet()) assertArrayEquals(want.get(n), got.get(n), n);
    }

    @Test void downloadsFromMojangAndBuilds() throws IOException {
        Path built = Installer.ensure(patch, v, "0.1.0", server, serving(mojang), log);
        assertIsMaster(built);
        assertEquals(Arrays.asList("https://one.example/s.jar"), fetched);
        assertTrue(said.contains("install.downloading") && said.contains("install.built"));
    }

    @Test void secondStartReusesTheBuiltJar() throws IOException {
        Path built = Installer.ensure(patch, v, "0.1.0", server, serving(mojang), log);
        long time = Files.getLastModifiedTime(built).toMillis();
        said.clear();
        fetched.clear();
        assertEquals(built, Installer.ensure(patch, v, "0.1.0", server, failing(), log));
        assertEquals(time, Files.getLastModifiedTime(built).toMillis());
        assertTrue(fetched.isEmpty());
        assertEquals(Arrays.asList("install.ready_cached"), said);
    }

    @Test void usesTheOwnersCopyInsteadOfDownloading() throws IOException {
        Files.copy(mojang, server.resolve("minecraft_server.1.12.2.jar"));
        assertIsMaster(Installer.ensure(patch, v, "0.1.0", server, failing(), log));
        assertTrue(fetched.isEmpty());
        assertTrue(said.contains("install.using_local"));
    }

    @Test void ignoresAnOwnersCopyWithTheWrongHash() throws IOException {
        Files.write(server.resolve("minecraft_server.1.12.2.jar"), new byte[]{1, 2, 3});
        assertIsMaster(Installer.ensure(patch, v, "0.1.0", server, serving(mojang), log));
        assertEquals(1, fetched.size());
    }

    @Test void triesTheNextMirrorAndRejectsWrongDownloads() throws IOException {
        Path wrong = Files.write(dir.resolve("wrong.jar"), new byte[]{9});
        Downloader web = (url, to) -> {
            fetched.add(url);
            Files.copy(url.contains("one") ? wrong : mojang, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        };
        assertIsMaster(Installer.ensure(patch, v, "0.1.0", server, web, log));
        assertEquals(2, fetched.size());
    }

    @Test void noInternetGivesAClearProblem() {
        Problem p = assertThrows(Problem.class, () -> Installer.ensure(patch, v, "0.1.0", server, failing(), log));
        assertEquals("error.download_failed", p.key);
        assertEquals(2, fetched.size());
    }

    @Test void damagedCacheIsRebuilt() throws IOException {
        Path built = Installer.ensure(patch, v, "0.1.0", server, serving(mojang), log);
        Files.write(built.resolveSibling(built.getFileName() + ".ok"), "something else".getBytes());
        said.clear();
        assertIsMaster(Installer.ensure(patch, v, "0.1.0", server, failing(), log));
        assertTrue(said.contains("install.building"));
    }

    @Test void newVersionReplacesTheOldBuild() throws IOException {
        Path old = Installer.ensure(patch, v, "0.1.0", server, serving(mojang), log);
        Path now = Installer.ensure(patch, v, "0.1.1", server, failing(), log);
        assertNotEquals(old, now);
        assertFalse(Files.exists(old), "the old build is deleted");
        assertIsMaster(now);
    }
}
