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
import java.util.function.Function;
import java.util.function.Predicate;

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

    /**
     * For Minecraft's own lookups (crafting, furnace, brewing stand): the query's answer from the live store, or null
     * while there is none, the rule is off or the feature switched itself off; a failure switches it off (Guard).
     */
    static RecipeData live(String where, Function<RecipeStore, RecipeData> query) {
        RecipeData[] found = new RecipeData[1];
        Guard.run(where, () -> {
            RecipeStore store = current;
            found[0] = store == null || !Rule.on() ? null : query.apply(store);
        });
        return found[0];
    }

    /** The saved recipe a crafting grid makes, or null. */
    RecipeData find(InventoryCrafting inv) {
        return find(ItemCodec.grid(inv), inv.getWidth(), inv.getHeight(), null);
    }

    /** The first saved furnace recipe (other than {@code except}) for that item, or null. */
    RecipeData findSmelting(Stuff input, String except) {
        return first(r -> !r.name.equals(except) && r.smelts(input));
    }

    /** The first saved brewing recipe (other than {@code except}) for that ingredient and bottle, or null. */
    RecipeData findBrewing(Stuff ingredient, Stuff bottle, String except) {
        return first(r -> !r.name.equals(except) && r.brews(ingredient, bottle));
    }

    /** A saved brewing recipe that takes the item as its ingredient, or null. */
    RecipeData findIngredient(Stuff ingredient) {
        return first(r -> r.kind == RecipeData.Kind.BREWING && r.cell(0).accepts(ingredient));
    }

    private RecipeData first(Predicate<RecipeData> test) {
        for (RecipeData r : new ArrayList<>(recipes.values())) if (test.test(r)) return r;
        return null;
    }

    /** The first saved recipe (other than {@code except}) a grid makes, or null. */
    RecipeData find(Stuff[] grid, int width, int height, String except) {
        return first(r -> !r.name.equals(except) && r.matches(grid, width, height));
    }
}
