package com.useleess.carpet.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The double-click setup without a window: start scripts, finding Java 8, the EULA file. */
class SetupTest {
    static final String SERVER = "useleess-carpet-1.12.2-server.jar";
    @TempDir Path dir;

    String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    Path write(String name, String text) throws IOException {
        return Files.write(dir.resolve(name), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test void findsTheOwnersScriptsAndTheirJar() throws IOException {
        write("start.bat", "@echo off\r\n\"C:\\Java 8\\bin\\java.exe\" -Xmx6G -XX:+UseG1GC -jar old-server.jar nogui\r\npause\r\n");
        write("run.sh", "#!/bin/sh\njava -jar \"my server.jar\" nogui\n");
        write("notes.bat", "echo hello\n");
        write("server.properties", "-jar fake.jar");
        List<StartScripts.Script> found = StartScripts.find(dir);
        assertEquals(2, found.size());
        assertEquals("run.sh", found.get(0).file.getFileName().toString());
        assertEquals("my server.jar", found.get(0).jar);
        assertEquals("old-server.jar", found.get(1).jar);
    }

    @Test void updateChangesOnlyTheJarAndKeepsABackup() throws IOException {
        String original = "@echo off\r\n\"C:\\Java 8\\bin\\java.exe\" -Xmx6G -XX:+UseG1GC -jar old-server.jar nogui\r\npause\r\n";
        Path bat = write("start.bat", original);
        StartScripts.Script s = StartScripts.find(dir).get(0);
        Path backup = StartScripts.update(s, SERVER);
        assertEquals("start.bat.backup", backup.getFileName().toString());
        assertEquals(original, read(backup));
        assertEquals(original.replace("old-server.jar", SERVER), read(bat));
        assertTrue(StartScripts.find(dir).get(0).starts(SERVER));
    }

    @Test void updateKeepsQuotesAndNeverOverwritesAnOldBackup() throws IOException {
        write("start.bat", "java -jar \"C:\\srv\\my server.jar\" nogui\r\n");
        write("start.bat.backup", "older backup");
        StartScripts.Script s = StartScripts.find(dir).get(0);
        assertEquals("start.bat.backup2", StartScripts.update(s, SERVER).getFileName().toString());
        assertEquals("older backup", read(dir.resolve("start.bat.backup")));
        assertEquals("java -jar \"" + SERVER + "\" nogui\r\n", read(dir.resolve("start.bat")));
    }

    @Test void aScriptAlreadyStartingTheServerJarNeedsNothing() throws IOException {
        write("start.bat", "java -Xmx2G -jar " + SERVER + " nogui\r\n");
        assertTrue(StartScripts.find(dir).get(0).starts(SERVER));
        write("start.bat", "java -jar C:\\srv\\" + SERVER + "\r\n");
        assertTrue(StartScripts.find(dir).get(0).starts(SERVER));
    }

    @Test void createsAWorkingScriptWhenThereIsNone() throws IOException {
        Path bat = StartScripts.create(dir, "C:\\Program Files\\Java\\jre1.8.0_451\\bin\\java.exe", SERVER, true);
        assertEquals(StartScripts.NAME_WINDOWS, bat.getFileName().toString());
        String text = read(bat);
        assertTrue(text.contains("\"C:\\Program Files\\Java\\jre1.8.0_451\\bin\\java.exe\" -Xmx2G -jar " + SERVER + " nogui"), text);
        assertTrue(text.contains("cd /d \"%~dp0\"") && text.contains("pause"), text);
        Path sh = StartScripts.create(dir, "java", SERVER, false);
        assertTrue(read(sh).contains("exec java -Xmx2G -jar " + SERVER + " nogui"));
        assertTrue(StartScripts.find(dir).stream().allMatch(s -> s.starts(SERVER)), "our own scripts count as found next time");
    }

    @Test void javaVersionsAreRead() {
        assertEquals(8, JavaFinder.major("java version \"1.8.0_451\"\nJava(TM) SE Runtime Environment"));
        assertEquals(8, JavaFinder.major("openjdk version \"1.8.0_452\"\nOpenJDK Runtime Environment (Temurin)"));
        assertEquals(21, JavaFinder.major("openjdk version \"21.0.2\" 2024-01-16"));
        assertEquals(17, JavaFinder.major("java version \"17\" 2021-09-14"));
        assertEquals(0, JavaFinder.major("'java' is not recognized"));
    }

    @Test void thisJavaAnswersItsVersion() {
        int major = JavaFinder.major(JavaFinder.versionOf(JavaFinder.current()));
        assertEquals(Integer.parseInt(System.getProperty("java.specification.version").replace("1.", "")), major);
    }

    @Test void javaIsLookedForInTheUsualPlacesIncludingMinecraftsOwn() throws IOException {
        Path pf = Files.createDirectories(dir.resolve("Program Files"));
        Files.createDirectories(pf.resolve("Eclipse Adoptium/jdk-8.0.452.9-hotspot/bin"));
        Files.createDirectories(pf.resolve("Java/jre1.8.0_451/bin"));
        Map<String, String> env = new HashMap<>();
        env.put("ProgramFiles", pf.toString());
        env.put("APPDATA", dir.resolve("Roaming").toString());
        env.put("LOCALAPPDATA", dir.resolve("Local").toString());
        env.put("JAVA_HOME", dir.resolve("jh").toString());
        List<Path> c = JavaFinder.candidates(env, true);
        assertEquals(dir.resolve("jh/bin/java.exe"), c.get(0), "JAVA_HOME first");
        assertTrue(c.contains(pf.resolve("Eclipse Adoptium/jdk-8.0.452.9-hotspot/bin/java.exe")));
        assertTrue(c.contains(pf.resolve("Java/jre1.8.0_451/bin/java.exe")));
        assertTrue(c.contains(dir.resolve("Roaming/.minecraft/runtime/jre-legacy/windows-x64/jre-legacy/bin/java.exe")));
        assertTrue(c.stream().anyMatch(p -> p.toString().contains("Microsoft.4297127D64EC6_8wekyb3d8bbwe")));
        assertTrue(JavaFinder.candidates(new HashMap<>(), false).stream().noneMatch(p -> p.toString().endsWith(".exe")));
        assertEquals(Paths.get("x", "javaw.exe").getFileName().toString(), "javaw.exe");
    }

    @Test void eulaIsWrittenAndRead() throws IOException {
        assertFalse(Eula.accepted(dir));
        write("eula.txt", "#comment\neula=false\n");
        assertFalse(Eula.accepted(dir));
        Eula.accept(dir);
        assertTrue(Eula.accepted(dir));
        assertTrue(read(dir.resolve("eula.txt")).contains(Eula.URL));
    }
}
