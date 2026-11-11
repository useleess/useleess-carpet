package com.useleess.carpet.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** One saved choice per player in a world file (UUID to a word: careful break "sneak" / "always", bot sleep "on"); game-free. */
public final class ChoiceStore {
    private final File file;
    private final Map<UUID, String> options = new TreeMap<>();

    public ChoiceStore(File file) {
        this.file = file;
    }

    /** Reads the file; a missing file is an empty store, a damaged one throws. */
    public static ChoiceStore open(File file) throws IOException {
        ChoiceStore store = new ChoiceStore(file);
        if (!file.isFile()) return store;
        try {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                store.options.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
            }
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | ClassCastException | UnsupportedOperationException e) {
            throw new IOException(e.toString(), e);
        }
        return store;
    }

    public String get(UUID player) {
        return options.get(player);
    }

    /** Sets (option) or removes (null) the player's entry and writes the file. */
    public void set(UUID player, String option) throws IOException {
        if (option == null) options.remove(player);
        else options.put(player, option);
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, String> e : options.entrySet()) root.addProperty(e.getKey().toString(), e.getValue());
        File folder = file.getAbsoluteFile().getParentFile();
        Files.createDirectories(folder.toPath());
        File temp = new File(folder, file.getName() + ".tmp");
        Files.write(temp.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
