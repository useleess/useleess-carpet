package com.useleess.carpet;

import com.useleess.carpet.server.BadRecipe;
import com.useleess.carpet.server.Product;
import com.useleess.carpet.server.RecipeData;
import com.useleess.carpet.server.RecipeFile;
import com.useleess.carpet.server.RecipeNames;
import com.useleess.carpet.server.Station;
import com.useleess.carpet.server.Stuff;
import com.useleess.carpet.server.TitleText;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** The recipe data, its file format and its matching: plain Java, no game. */
class RecipeTest {
    static final Stuff STICK = new Stuff("minecraft:stick", 0), DIAMOND = new Stuff("minecraft:diamond", 0), PLANK = new Stuff("minecraft:planks", 1);
    static final Product SWORD = new Product("minecraft:diamond_sword", 0, 1, null);

    static Stuff[] grid(Stuff... cells) {
        return Arrays.copyOf(cells, 9);
    }

    static RecipeData sword() {
        return RecipeData.fromGrid("sword", grid(null, DIAMOND, null, null, DIAMOND, null, null, STICK, null), 3, false, SWORD);
    }

    @Test void gridIsTrimmedToItsPattern() {
        RecipeData r = sword();
        assertEquals(RecipeData.Kind.SHAPED, r.kind);
        assertEquals(1, r.width);
        assertEquals(3, r.height);
        assertEquals(DIAMOND, r.cell(0));
        assertEquals(STICK, r.cell(2));
        RecipeData corner = RecipeData.fromGrid("c", grid(null, null, null, null, null, null, null, DIAMOND, STICK), 3, false, SWORD);
        assertEquals(2, corner.width);
        assertEquals(1, corner.height);
        assertThrows(IllegalArgumentException.class, () -> RecipeData.fromGrid("e", grid(), 3, false, SWORD));
        assertThrows(IllegalArgumentException.class, () -> RecipeData.fromGrid("e", grid(), 3, true, SWORD));
    }

    @Test void shapedMatchesAnywhereInTheGridAndMirrored() {
        RecipeData r = RecipeData.fromGrid("l", grid(DIAMOND, STICK, null, null, null, null, null, null, null), 3, false, SWORD);
        assertTrue(r.matches(grid(DIAMOND, STICK), 3, 3));
        assertTrue(r.matches(grid(null, null, null, null, DIAMOND, STICK), 3, 3));
        assertTrue(r.matches(grid(STICK, DIAMOND), 3, 3), "mirrored like vanilla");
        assertFalse(r.matches(grid(DIAMOND, STICK, null, null, null, null, null, null, STICK), 3, 3), "an extra item");
        assertFalse(r.matches(grid(DIAMOND, null, STICK), 3, 3), "a different shape");
        assertFalse(r.matches(grid(DIAMOND, DIAMOND), 3, 3));
        assertTrue(r.matches(new Stuff[]{DIAMOND, STICK, null, null}, 2, 2), "in the 2x2 inventory grid");
    }

    @Test void shapelessMatchesInAnyOrder() {
        RecipeData r = RecipeData.fromGrid("any", grid(DIAMOND, STICK, STICK), 3, true, SWORD);
        assertEquals(RecipeData.Kind.SHAPELESS, r.kind);
        assertTrue(r.matches(grid(STICK, null, DIAMOND, null, null, STICK), 3, 3));
        assertFalse(r.matches(grid(STICK, DIAMOND), 3, 3));
        assertFalse(r.matches(grid(STICK, DIAMOND, DIAMOND), 3, 3));
        assertFalse(r.matches(grid(STICK, DIAMOND, STICK, STICK), 3, 3));
    }

    @Test void metaMustMatchUnlessAnyMeta() {
        RecipeData exact = RecipeData.fromGrid("m", grid(PLANK), 3, false, SWORD);
        assertTrue(exact.matches(grid(PLANK), 3, 3));
        assertFalse(exact.matches(grid(new Stuff("minecraft:planks", 2)), 3, 3));
        RecipeData any = RecipeData.shapeless("a", Collections.singletonList(new Stuff("minecraft:planks", Stuff.ANY_META)), SWORD);
        assertTrue(any.matches(grid(new Stuff("minecraft:planks", 2)), 3, 3));
        assertFalse(any.matches(grid(STICK), 3, 3));
    }

    @Test void toGridKeepsTheShapeAtTheTopLeft() {
        Stuff[] back = sword().toGrid();
        assertEquals(DIAMOND, back[0]);
        assertEquals(DIAMOND, back[3]);
        assertEquals(STICK, back[6]);
        assertNull(back[1]);
        Stuff[] loose = RecipeData.fromGrid("a", grid(STICK, null, STICK, DIAMOND), 3, true, SWORD).toGrid();
        assertArrayEquals(new Stuff[]{STICK, STICK, DIAMOND, null, null, null, null, null, null}, loose);
    }

    @Test void shapedFileRoundTrip() throws BadRecipe {
        RecipeData r = sword();
        String text = RecipeFile.write(r);
        assertTrue(text.contains("\"shaped\"") && text.contains("\"pattern\""));
        RecipeData back = RecipeFile.read("sword", text);
        assertEquals(r.kind, back.kind);
        assertEquals(1, back.width);
        assertEquals(3, back.height);
        for (int i = 0; i < 3; i++) assertEquals(r.cell(i), back.cell(i));
        assertEquals(SWORD, back.result);
        assertEquals(text, RecipeFile.write(back));
    }

    @Test void shapelessFileRoundTrip() throws BadRecipe {
        RecipeData r = RecipeData.fromGrid("any", grid(DIAMOND, STICK, null, STICK), 3, true, SWORD);
        RecipeData back = RecipeFile.read("any", RecipeFile.write(r));
        assertEquals(RecipeData.Kind.SHAPELESS, back.kind);
        assertEquals(r.ingredients(), back.ingredients());
    }

    static final Stuff WATER = new Stuff("minecraft:potion", 0, "minecraft:water"), AWKWARD = new Stuff("minecraft:potion", 0, "minecraft:awkward");
    static final Product NUGGETS = new Product("minecraft:gold_nugget", 0, 3, null);

    @Test void potionTypeMustMatchWhenNamed() {
        assertTrue(WATER.accepts(WATER));
        assertFalse(WATER.accepts(AWKWARD));
        assertTrue(new Stuff("minecraft:potion", 0).accepts(AWKWARD), "no potion type named = any");
        assertFalse(WATER.accepts(null));
        assertNotEquals(WATER, AWKWARD);
        RecipeData tipped = RecipeData.fromGrid("t", grid(WATER, STICK), 3, true, SWORD);
        assertTrue(tipped.matches(grid(STICK, WATER), 3, 3));
        assertFalse(tipped.matches(grid(STICK, AWKWARD), 3, 3));
    }

    @Test void smeltingMatchesOnlyItsItemAndNeverACraftingGrid() {
        RecipeData r = RecipeData.smelting("s", DIAMOND, NUGGETS);
        assertTrue(r.smelts(DIAMOND));
        assertFalse(r.smelts(STICK));
        assertFalse(r.smelts(null));
        assertFalse(r.matches(grid(DIAMOND), 3, 3), "a furnace recipe is not a crafting recipe");
        assertFalse(r.brews(DIAMOND, WATER));
        assertFalse(sword().smelts(DIAMOND));
    }

    @Test void brewingNeedsIngredientAndBottle() {
        RecipeData r = RecipeData.brewing("b", DIAMOND, WATER, NUGGETS);
        assertTrue(r.brews(DIAMOND, WATER));
        assertFalse(r.brews(DIAMOND, AWKWARD));
        assertFalse(r.brews(STICK, WATER));
        assertFalse(r.brews(DIAMOND, null));
        assertFalse(r.matches(grid(DIAMOND, WATER), 3, 3));
        assertFalse(r.smelts(DIAMOND));
    }

    @Test void stationsUseTheirOwnCells() {
        Stuff[] g = new Stuff[9];
        assertFalse(Station.SMELTING.complete(g));
        g[Station.SMELTING.cell(0)] = DIAMOND;
        assertTrue(Station.SMELTING.complete(g));
        assertTrue(Station.CRAFTING.complete(g));
        assertFalse(Station.BREWING.complete(g));
        assertTrue(Station.SMELTING.uses(4) && !Station.SMELTING.uses(0));
        assertTrue(Station.BREWING.uses(1) && Station.BREWING.uses(7) && !Station.BREWING.uses(4));
        for (int i = 0; i < 9; i++) assertTrue(Station.CRAFTING.uses(i));
        assertFalse(Station.BREWING.allows(Station.BREWING.cell(1), "minecraft:diamond"), "the bottle slot takes bottles only");
        assertTrue(Station.BREWING.allows(Station.BREWING.cell(1), "minecraft:splash_potion"));
        assertTrue(Station.BREWING.allows(Station.BREWING.cell(0), "minecraft:diamond"));
        assertTrue(Station.SMELTING.allows(4, "minecraft:diamond"));
        assertEquals(Station.CRAFTING, Station.BREWING.next(1));
        assertEquals(Station.BREWING, Station.CRAFTING.next(-1));
        assertEquals(Station.SMELTING, Station.of(RecipeData.Kind.SMELTING));
        assertEquals(Station.CRAFTING, Station.of(RecipeData.Kind.SHAPELESS));
    }

    @Test void stationRecipesComeFromTheEditorGridAndGoBack() {
        Stuff[] g = new Stuff[9];
        g[1] = DIAMOND;
        g[7] = WATER;
        RecipeData brew = Station.BREWING.recipe("b", g, false, NUGGETS);
        assertEquals(RecipeData.Kind.BREWING, brew.kind);
        assertArrayEquals(g, brew.toGrid());
        Stuff[] s = new Stuff[9];
        s[4] = DIAMOND;
        RecipeData smelt = Station.SMELTING.recipe("s", s, true, NUGGETS);
        assertEquals(RecipeData.Kind.SMELTING, smelt.kind);
        assertArrayEquals(s, smelt.toGrid());
        assertEquals(RecipeData.Kind.SHAPELESS, Station.CRAFTING.recipe("c", s, true, NUGGETS).kind);
    }

    @Test void smeltingAndBrewingFileRoundTrip() throws BadRecipe {
        RecipeData smelt = RecipeData.smelting("s", PLANK, NUGGETS);
        String text = RecipeFile.write(smelt);
        assertTrue(text.contains("\"smelting\"") && text.contains("\"input\""));
        RecipeData back = RecipeFile.read("s", text);
        assertEquals(RecipeData.Kind.SMELTING, back.kind);
        assertEquals(PLANK, back.cell(0));
        assertEquals(NUGGETS, back.result);
        assertEquals(text, RecipeFile.write(back));
        RecipeData brew = RecipeData.brewing("b", DIAMOND, WATER, NUGGETS);
        text = RecipeFile.write(brew);
        assertTrue(text.contains("\"brewing\"") && text.contains("\"potion\": \"minecraft:water\""));
        back = RecipeFile.read("b", text);
        assertEquals(RecipeData.Kind.BREWING, back.kind);
        assertEquals(DIAMOND, back.cell(0));
        assertEquals(WATER, back.cell(1));
        assertEquals(text, RecipeFile.write(back));
        assertFalse(RecipeFile.write(sword()).contains("potion"), "no potion line for plain items");
    }

    @Test void resultNbtAndCountSurvive() throws BadRecipe {
        Product named = new Product("minecraft:diamond_sword", 0, 3, "{display:{Name:\"Zap \\\"x\\\"\"}}");
        RecipeData r = RecipeData.fromGrid("n", grid(DIAMOND), 3, false, named);
        RecipeData back = RecipeFile.read("n", RecipeFile.write(r));
        assertEquals(named, back.result);
    }

    @Test void aBrokenFileIsRefusedWithAReasonKey() {
        assertBad("recipes.bad.syntax", "{ not json");
        assertBad("recipes.bad.not_object", "[1, 2]");
        assertBad("recipes.bad.field", "{}");
        assertBad("recipes.bad.type", "{\"type\":\"banana\",\"result\":{\"item\":\"a:b\"}}");
        assertBad("recipes.bad.pattern", "{\"type\":\"shaped\",\"pattern\":[\"ab\",\"a\"],\"key\":{},\"result\":{\"item\":\"a:b\"}}");
        assertBad("recipes.bad.pattern", "{\"type\":\"shaped\",\"pattern\":[\"  \"],\"key\":{},\"result\":{\"item\":\"a:b\"}}");
        assertBad("recipes.bad.key", "{\"type\":\"shaped\",\"pattern\":[\"a\"],\"key\":{},\"result\":{\"item\":\"a:b\"}}");
        assertBad("recipes.bad.ingredients", "{\"type\":\"shapeless\",\"ingredients\":[],\"result\":{\"item\":\"a:b\"}}");
        assertBad("recipes.bad.count", "{\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"a:b\"}],\"result\":{\"item\":\"a:b\",\"count\":99}}");
        assertBad("recipes.bad.field", "{\"type\":\"shapeless\",\"ingredients\":[{\"item\":\"a:b\"}],\"result\":{\"item\":\"a:b\",\"meta\":\"x\"}}");
        assertBad("recipes.bad.field", "{\"type\":\"smelting\",\"result\":{\"item\":\"a:b\"}}");
        assertBad("recipes.bad.field", "{\"type\":\"brewing\",\"input\":{\"item\":\"minecraft:potion\"},\"result\":{\"item\":\"a:b\"}}");
        assertBad("recipes.bad.bottle", "{\"type\":\"brewing\",\"ingredient\":{\"item\":\"a:b\"},\"input\":{\"item\":\"minecraft:dirt\"},\"result\":{\"item\":\"a:b\"}}");
    }

    private static void assertBad(String key, String text) {
        BadRecipe e = assertThrows(BadRecipe.class, () -> RecipeFile.read("x", text));
        assertEquals(key, e.key);
    }

    @Test void nameRules() {
        assertTrue(RecipeNames.valid("diamond_sword_2"));
        assertFalse(RecipeNames.valid("Diamond"));
        assertFalse(RecipeNames.valid("../etc"));
        assertFalse(RecipeNames.valid(""));
        assertFalse(RecipeNames.valid("a b"));
        assertEquals("diamond_sword", RecipeNames.of("minecraft:diamond_sword"));
        assertEquals("some_item", RecipeNames.of("Mod:Some-Item"));
        assertTrue(RecipeNames.valid(RecipeNames.of("minecraft:" + String.join("", Collections.nCopies(100, "x")))));
        assertEquals("diamond_sword", RecipeNames.unique("diamond_sword", new HashSet<String>()));
        assertEquals("diamond_sword_2", RecipeNames.unique("diamond_sword", new HashSet<>(Arrays.asList("diamond_sword"))));
        assertEquals("diamond_sword_3", RecipeNames.unique("diamond_sword", new HashSet<>(Arrays.asList("diamond_sword", "diamond_sword_2"))));
    }

    @Test void titleStatusFitsTheWindow() {
        assertEquals("Saved: golden_apple", TitleText.fit("Saved: golden_apple"));
        String cut = TitleText.fit("Saved: light_weighted_pressure_plate");
        assertEquals(TitleText.MAX, cut.length());
        assertEquals("Saved: light_weighted_press...", cut);
        assertEquals(TitleText.MAX, TitleText.fit(String.join("", Collections.nCopies(TitleText.MAX, "x"))).length());
    }
}
