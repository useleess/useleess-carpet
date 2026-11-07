package com.useleess.carpet.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One saved recipe, in plain Java: shaped (a trimmed rectangle of ingredients, empty cells null), shapeless
 * (any order), smelting (one item) or brewing (ingredient + bottle). {@link #fromGrid} trims the editor's 3x3 grid;
 * {@link #matches} is the crafting grid check, {@link #smelts} the furnace's, {@link #brews} the brewing stand's.
 */
public final class RecipeData {
    public enum Kind { SHAPED, SHAPELESS, SMELTING, BREWING }

    public final String name;
    public final Kind kind;
    /**
     * Shaped: width x height cells, row by row; empty cells are null. Shapeless: width = list size, height 1.
     * Smelting: the one item (1x1). Brewing: ingredient, bottle (1x2).
     */
    public final int width, height;
    private final Stuff[] cells;
    public final Product result;

    private RecipeData(String name, Kind kind, int width, int height, Stuff[] cells, Product result) {
        this.name = name;
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.cells = cells;
        this.result = result;
    }

    /** From the editor's grid (row by row, null = empty). Throws when the grid is empty. */
    public static RecipeData fromGrid(String name, Stuff[] grid, int gridWidth, boolean anyOrder, Product result) {
        if (anyOrder) {
            List<Stuff> list = new ArrayList<>();
            for (Stuff s : grid) if (s != null) list.add(s);
            if (list.isEmpty()) throw new IllegalArgumentException("empty grid");
            return shapeless(name, list, result);
        }
        int[] box = bounds(grid, gridWidth, grid.length / gridWidth);
        if (box == null) throw new IllegalArgumentException("empty grid");
        return new RecipeData(name, Kind.SHAPED, box[2], box[3], crop(grid, gridWidth, box), result);
    }

    /** Shaped recipe from already trimmed cells (file reading). */
    public static RecipeData shaped(String name, int width, int height, Stuff[] cells, Product result) {
        return new RecipeData(name, Kind.SHAPED, width, height, cells.clone(), result);
    }

    public static RecipeData shapeless(String name, List<Stuff> items, Product result) {
        return new RecipeData(name, Kind.SHAPELESS, items.size(), 1, items.toArray(new Stuff[0]), result);
    }

    public static RecipeData smelting(String name, Stuff input, Product result) {
        return new RecipeData(name, Kind.SMELTING, 1, 1, new Stuff[]{input}, result);
    }

    public static RecipeData brewing(String name, Stuff ingredient, Stuff bottle, Product result) {
        return new RecipeData(name, Kind.BREWING, 1, 2, new Stuff[]{ingredient, bottle}, result);
    }

    public boolean crafting() {
        return kind == Kind.SHAPED || kind == Kind.SHAPELESS;
    }

    public Stuff cell(int index) {
        return cells[index];
    }

    public List<Stuff> ingredients() {
        List<Stuff> out = new ArrayList<>();
        for (Stuff s : cells) if (s != null) out.add(s);
        return Collections.unmodifiableList(out);
    }

    /**
     * The recipe laid into a 3x3 editor grid (shaped keeps its shape at the top left, shapeless fills in order, the
     * furnace and the brewing stand use their {@link Station}'s cells).
     */
    public Stuff[] toGrid() {
        Stuff[] grid = new Stuff[9];
        if (kind == Kind.SHAPED) {
            for (int r = 0; r < height; r++) for (int c = 0; c < width; c++) grid[r * 3 + c] = cells[r * width + c];
        } else if (kind == Kind.SHAPELESS) {
            System.arraycopy(cells, 0, grid, 0, cells.length);
        } else {
            for (int i = 0; i < cells.length; i++) grid[Station.of(kind).cell(i)] = cells[i];
        }
        return grid;
    }

    /** Whether a furnace smelts that item (null = empty) with this recipe. */
    public boolean smelts(Stuff input) {
        return kind == Kind.SMELTING && cells[0].accepts(input);
    }

    /** Whether a brewing stand turns that bottle into this recipe's result with that ingredient. */
    public boolean brews(Stuff ingredient, Stuff bottle) {
        return kind == Kind.BREWING && cells[0].accepts(ingredient) && cells[1].accepts(bottle);
    }

    /** Whether a crafting grid (row by row, null = empty) makes this recipe. */
    public boolean matches(Stuff[] grid, int gridWidth, int gridHeight) {
        if (!crafting()) return false;
        if (kind == Kind.SHAPELESS) return matchesAnyOrder(grid);
        int[] box = bounds(grid, gridWidth, gridHeight);
        if (box == null || box[2] != width || box[3] != height) return false;
        Stuff[] actual = crop(grid, gridWidth, box);
        return sameCells(actual, false) || sameCells(actual, true);
    }

    private boolean sameCells(Stuff[] actual, boolean mirrored) {
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                Stuff want = cells[r * width + c], got = actual[r * width + (mirrored ? width - 1 - c : c)];
                if (want == null ? got != null : got == null || !want.accepts(got)) return false;
            }
        }
        return true;
    }

    private boolean matchesAnyOrder(Stuff[] grid) {
        List<Stuff> have = new ArrayList<>();
        for (Stuff s : grid) if (s != null) have.add(s);
        return have.size() == cells.length && assign(0, have, new boolean[have.size()]);
    }

    private boolean assign(int next, List<Stuff> have, boolean[] used) {
        if (next == cells.length) return true;
        for (int i = 0; i < have.size(); i++) {
            if (used[i] || !cells[next].accepts(have.get(i))) continue;
            used[i] = true;
            if (assign(next + 1, have, used)) return true;
            used[i] = false;
        }
        return false;
    }

    /** {firstRow, firstColumn, width, height} of the non-empty cells, or null when there are none. */
    private static int[] bounds(Stuff[] grid, int gridWidth, int gridHeight) {
        int r0 = gridHeight, r1 = -1, c0 = gridWidth, c1 = -1;
        for (int r = 0; r < gridHeight; r++) {
            for (int c = 0; c < gridWidth; c++) {
                if (grid[r * gridWidth + c] == null) continue;
                r0 = Math.min(r0, r);
                r1 = Math.max(r1, r);
                c0 = Math.min(c0, c);
                c1 = Math.max(c1, c);
            }
        }
        return r1 < 0 ? null : new int[]{r0, c0, c1 - c0 + 1, r1 - r0 + 1};
    }

    private static Stuff[] crop(Stuff[] grid, int gridWidth, int[] box) {
        Stuff[] out = new Stuff[box[2] * box[3]];
        for (int r = 0; r < box[3]; r++) for (int c = 0; c < box[2]; c++) out[r * box[2] + c] = grid[(box[0] + r) * gridWidth + box[1] + c];
        return out;
    }
}
