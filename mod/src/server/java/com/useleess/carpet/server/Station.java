package com.useleess.carpet.server;

import java.util.Arrays;
import java.util.List;

/**
 * Which block a recipe is for (user 2026-11-07, #7 #8): the crafting table (the whole 3x3 grid), the furnace (the
 * grid's middle cell = what is smelted) or the brewing stand (top middle = the ingredient, bottom middle = the
 * bottle). The editor's grid cells each station uses are listed here. Game-free.
 */
public enum Station {
    CRAFTING("crafting", 0, 1, 2, 3, 4, 5, 6, 7, 8), SMELTING("smelting", 4), BREWING("brewing", 1, 7);

    /** What a brewing stand's bottle slots hold (Minecraft's own rule for those slots). */
    private static final List<String> BOTTLES = Arrays.asList("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:glass_bottle");

    /** Language key suffix: {@code station.<key>}. */
    public final String key;
    private final int[] cells;

    Station(String key, int... cells) {
        this.key = key;
        this.cells = cells;
    }

    public Station next(int by) {
        Station[] all = values();
        return all[(ordinal() + by + all.length) % all.length];
    }

    public static Station of(RecipeData.Kind kind) {
        switch (kind) {
            case SMELTING: return SMELTING;
            case BREWING: return BREWING;
            default: return CRAFTING;
        }
    }

    /** Whether the station uses that cell (0..8) of the editor's grid. */
    public boolean uses(int cell) {
        for (int c : cells) if (c == cell) return true;
        return false;
    }

    /** The grid cell of the station's n-th input (smelting: 0 = the item; brewing: 0 = ingredient, 1 = bottle). */
    public int cell(int n) {
        return cells[n];
    }

    /** Whether an item may stand in the given cell: a brewing stand's bottle cell takes only bottles and potions. */
    public boolean allows(int cell, String item) {
        return this != BREWING || cell != cell(1) || bottle(item);
    }

    public static boolean bottle(String item) {
        return BOTTLES.contains(item);
    }

    /** Whether the grid holds enough for a recipe: crafting any item, the furnace and the brewing stand every input. */
    public boolean complete(Stuff[] grid) {
        boolean any = false, all = true;
        for (int c : cells) {
            any |= grid[c] != null;
            all &= grid[c] != null;
        }
        return this == CRAFTING ? any : all;
    }

    /** The recipe the editor's grid makes (call {@link #complete} first). */
    public RecipeData recipe(String name, Stuff[] grid, boolean anyOrder, Product result) {
        switch (this) {
            case SMELTING: return RecipeData.smelting(name, grid[cell(0)], result);
            case BREWING: return RecipeData.brewing(name, grid[cell(0)], grid[cell(1)], result);
            default: return RecipeData.fromGrid(name, grid, 3, anyOrder, result);
        }
    }
}
