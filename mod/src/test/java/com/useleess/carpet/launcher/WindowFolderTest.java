package com.useleess.carpet.launcher;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The installer window works next to its jar, never in the folder Java was started in (system32 on a double-click). */
class WindowFolderTest {
    @Test void theJarsFolderWinsOverTheStartFolder() {
        Path jar = Paths.get("servers", "carpet", "useleess-carpet.jar").toAbsolutePath();
        Path system32 = Paths.get("C:", "Windows", "system32");
        assertEquals(jar.getParent(), Main.windowFolder(jar, system32));
    }

    @Test void withoutAJarTheStartFolderIsUsed() {
        Path start = Paths.get("some", "folder");
        assertEquals(start.toAbsolutePath(), Main.windowFolder(null, start));
    }
}
