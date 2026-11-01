package com.useleess.carpet.server;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Names of saved recipes: the file name without ".json". Game-free. */
public final class RecipeNames {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_]{1,64}");

    private RecipeNames() {}

    public static boolean valid(String name) {
        return VALID.matcher(name).matches();
    }

    /** The name a result's registry name gives ("minecraft:diamond_sword" -> "diamond_sword"). */
    public static String of(String registryName) {
        String path = registryName.substring(registryName.indexOf(':') + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return path.isEmpty() ? "recipe" : path.length() > 60 ? path.substring(0, 60) : path;
    }

    /** The name itself, else name_2, name_3 ... the first not taken. */
    public static String unique(String base, Set<String> taken) {
        if (!taken.contains(base)) return base;
        for (int n = 2; ; n++) if (!taken.contains(base + "_" + n)) return base + "_" + n;
    }
}
