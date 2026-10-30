package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** When the launcher deletes itself (the real deletion of a running jar is checked in ingame-test.sh). */
class SelfRemoverTest {
    @TempDir Path dir;
    final List<String> said = new ArrayList<>();
    final Downloader.Log log = (key, args) -> said.add(key);

    @Test void launcherInTheServerFolderIsDeleted() throws IOException {
        Path self = Files.write(dir.resolve("useleess-carpet-1.12.2-0.1.2.jar"), new byte[]{1});
        Path server = Files.write(dir.resolve("useleess-carpet-1.12.2-server.jar"), new byte[]{2});
        SelfRemover.remove(self, dir, server, log);
        assertFalse(Files.exists(self));
        assertTrue(Files.exists(server));
        assertEquals(Arrays.asList("install.installer_removed", "install.next_start"), said);
    }

    @Test void launcherElsewhereIsKept() throws IOException {
        Path elsewhere = Files.createDirectories(dir.resolve("Downloads"));
        Path self = Files.write(elsewhere.resolve("useleess-carpet-1.12.2-0.1.2.jar"), new byte[]{1});
        Path folder = Files.createDirectories(dir.resolve("server"));
        SelfRemover.remove(self, folder, folder.resolve("useleess-carpet-1.12.2-server.jar"), log);
        assertTrue(Files.exists(self));
        assertTrue(said.isEmpty());
    }

    @Test void neverDeletesTheServerJarOrWithoutAJar() throws IOException {
        Path server = Files.write(dir.resolve("useleess-carpet-1.12.2-server.jar"), new byte[]{2});
        SelfRemover.remove(server, dir, server, log);
        SelfRemover.remove(null, dir, server, log);
        assertTrue(Files.exists(server));
        assertTrue(said.isEmpty());
    }

    @Test void runningFromClassesIsNotAJar() {
        assertNull(SelfRemover.selfJar(), "tests run from class folders: nothing to delete");
    }
}
