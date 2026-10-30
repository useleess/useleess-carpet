package com.useleess.carpet.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minecraft's eula.txt: the server refuses to start until it says eula=true (the player agrees in our window). */
final class Eula {
    static final String URL = "https://aka.ms/MinecraftEULA";
    static final String FILE = "eula.txt";

    private Eula() {}

    static boolean accepted(Path folder) {
        Path f = folder.resolve(FILE);
        try {
            return Files.isRegularFile(f) && new String(Files.readAllBytes(f), StandardCharsets.UTF_8).replace(" ", "").contains("eula=true");
        } catch (IOException e) {
            return false;
        }
    }

    /** The same file the server itself writes, set to true. */
    static void accept(Path folder) throws IOException {
        String text = "#By changing the setting below to TRUE you are indicating your agreement to our EULA (" + URL + ").\n"
                + "#" + new java.util.Date() + "\n"
                + "eula=true\n";
        Files.write(folder.resolve(FILE), text.getBytes(StandardCharsets.UTF_8));
    }
}
