package com.useleess.carpet.server;

import net.minecraft.inventory.InventoryCrafting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The saved recipes of this world: {@code <world>/useleess-carpet/recipes/<name>.json}, loaded at server start; saving
 * and deleting change the live list at once (the one registered {@link CustomRecipes} reads it at match time).
 * A broken file is skipped with a notice naming it; the others load.
 */
final class RecipeStore {
    private static final String EXTENSION = ".json";
    static RecipeStore current;

    private final File dir;
    private final Map<String, RecipeData> recipes = new TreeMap<>();

    private RecipeStore(File dir) {
        this.dir = dir;
    }

    static RecipeStore open(File dir) {
        RecipeStore store = new RecipeStore(dir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(EXTENSION));
        if (files == null) return store;
        Arrays.sort(files);
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - EXTENSION.length());
            try {
                if (!RecipeNames.valid(name)) throw new BadRecipe("recipes.bad.name");
                RecipeData data = RecipeFile.read(name, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                ItemCodec.check(data);
                store.recipes.put(name, data);
            } catch (BadRecipe e) {
                Guard.notice(ServerText.get("recipes.file_skipped", file.getName(), ServerText.get(e.key, e.args)));
            } catch (IOException e) {
                Guard.notice(ServerText.get("recipes.file_skipped", file.getName(), String.valueOf(e.getMessage())));
            }
        }
        return store;
    }

    Collection<RecipeData> all() {
        return Collections.unmodifiableCollection(recipes.values());
    }

    Set<String> names() {
        return Collections.unmodifiableSet(recipes.keySet());
    }

    RecipeData get(String name) {
        return recipes.get(name);
    }

    /** Written to a temporary file first, so a crash never leaves half a recipe. */
    void save(RecipeData data) throws IOException {
        Files.createDirectories(dir.toPath());
        File target = new File(dir, data.name + EXTENSION);
        File temp = new File(dir, data.name + EXTENSION + ".tmp");
        Files.write(temp.toPath(), RecipeFile.write(data).getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        recipes.put(data.name, data);
    }

    void delete(String name) throws IOException {
        Files.deleteIfExists(new File(dir, name + EXTENSION).toPath());
        recipes.remove(name);
    }

    /** The saved recipe a crafting grid makes, or null. */
    RecipeData find(InventoryCrafting inv) {
        return find(ItemCodec.grid(inv), inv.getWidth(), inv.getHeight(), null);
    }

    /** The first saved recipe (other than {@code except}) a grid makes, or null. */
    RecipeData find(Stuff[] grid, int width, int height, String except) {
        for (RecipeData r : new ArrayList<>(recipes.values())) {
            if (!r.name.equals(except) && r.matches(grid, width, height)) return r;
        }
        return null;
    }
}
