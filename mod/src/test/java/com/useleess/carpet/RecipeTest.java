package com.useleess.carpet;

import com.useleess.carpet.server.BadRecipe;
import com.useleess.carpet.server.Product;
import com.useleess.carpet.server.RecipeData;
import com.useleess.carpet.server.RecipeFile;
import com.useleess.carpet.server.RecipeNames;
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
        assertTrue(r.shaped);
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
        assertFalse(r.shaped);
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
        assertEquals(r.shaped, back.shaped);
        assertEquals(1, back.width);
        assertEquals(3, back.height);
        for (int i = 0; i < 3; i++) assertEquals(r.cell(i), back.cell(i));
        assertEquals(SWORD, back.result);
        assertEquals(text, RecipeFile.write(back));
    }

    @Test void shapelessFileRoundTrip() throws BadRecipe {
        RecipeData r = RecipeData.fromGrid("any", grid(DIAMOND, STICK, null, STICK), 3, true, SWORD);
        RecipeData back = RecipeFile.read("any", RecipeFile.write(r));
        assertFalse(back.shaped);
        assertEquals(r.ingredients(), back.ingredients());
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
