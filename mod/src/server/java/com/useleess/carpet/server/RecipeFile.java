package com.useleess.carpet.server;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Our own small recipe file format ({@code recipes/<name>.json}):
 * <pre>
 * {"type": "shaped", "pattern": ["aa", "b "], "key": {"a": {"item": "minecraft:stick", "meta": 0}, ...},
 *  "result": {"item": "minecraft:diamond_sword", "meta": 0, "count": 1, "nbt": "{display:{Name:\"x\"}}"}}
 * {"type": "shapeless", "ingredients": [{"item": ..., "meta": ...}, ...], "result": {...}}
 * {"type": "smelting", "input": {"item": ..., "meta": ...}, "result": {...}}
 * {"type": "brewing", "ingredient": {...}, "input": {"item": "minecraft:potion", "meta": 0, "potion": "minecraft:water"}, "result": {...}}
 * </pre>
 * An item entry may name a potion type ("potion"); without one any potion type is taken.
 * Game-free; every problem is a {@link BadRecipe} with a language key.
 */
public final class RecipeFile {
    private static final String LETTERS = "abcdefghi";

    private RecipeFile() {}

    public static String write(RecipeData r) {
        JsonObject o = new JsonObject();
        o.addProperty("type", r.kind.name().toLowerCase(Locale.ROOT));
        if (r.kind == RecipeData.Kind.SMELTING) {
            o.add("input", stuff(r.cell(0)));
        } else if (r.kind == RecipeData.Kind.BREWING) {
            o.add("ingredient", stuff(r.cell(0)));
            o.add("input", stuff(r.cell(1)));
        } else if (r.kind == RecipeData.Kind.SHAPED) {
            Map<Stuff, Character> letters = new LinkedHashMap<>();
            JsonArray rows = new JsonArray();
            for (int row = 0; row < r.height; row++) {
                StringBuilder line = new StringBuilder();
                for (int c = 0; c < r.width; c++) {
                    Stuff s = r.cell(row * r.width + c);
                    if (s != null && !letters.containsKey(s)) letters.put(s, LETTERS.charAt(letters.size()));
                    line.append(s == null ? ' ' : letters.get(s));
                }
                rows.add(new JsonPrimitive(line.toString()));
            }
            o.add("pattern", rows);
            JsonObject key = new JsonObject();
            for (Map.Entry<Stuff, Character> e : letters.entrySet()) key.add(String.valueOf(e.getValue()), stuff(e.getKey()));
            o.add("key", key);
        } else {
            JsonArray list = new JsonArray();
            for (Stuff s : r.ingredients()) list.add(stuff(s));
            o.add("ingredients", list);
        }
        JsonObject res = new JsonObject();
        res.addProperty("item", r.result.item);
        res.addProperty("meta", r.result.meta);
        res.addProperty("count", r.result.count);
        if (r.result.nbt != null) res.addProperty("nbt", r.result.nbt);
        o.add("result", res);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
    }

    private static JsonObject stuff(Stuff s) {
        JsonObject o = new JsonObject();
        o.addProperty("item", s.item);
        o.addProperty("meta", s.meta);
        if (s.potion != null) o.addProperty("potion", s.potion);
        return o;
    }

    public static RecipeData read(String name, String text) throws BadRecipe {
        JsonObject o;
        try {
            JsonElement e = new JsonParser().parse(text);
            if (!e.isJsonObject()) throw new BadRecipe("recipes.bad.not_object");
            o = e.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new BadRecipe("recipes.bad.syntax", String.valueOf(e.getMessage()));
        }
        Product result = product(object(o, "result"));
        String type = string(o, "type");
        if (type.equals("smelting")) return RecipeData.smelting(name, stuff(object(o, "input")), result);
        if (type.equals("brewing")) {
            Stuff bottle = stuff(object(o, "input"));
            if (!Station.bottle(bottle.item)) throw new BadRecipe("recipes.bad.bottle", bottle.item);
            return RecipeData.brewing(name, stuff(object(o, "ingredient")), bottle, result);
        }
        if (type.equals("shapeless")) {
            List<Stuff> items = new ArrayList<>();
            for (JsonElement e : array(o, "ingredients")) items.add(stuff(e));
            if (items.isEmpty() || items.size() > 9) throw new BadRecipe("recipes.bad.ingredients");
            return RecipeData.shapeless(name, items, result);
        }
        if (!type.equals("shaped")) throw new BadRecipe("recipes.bad.type", type);
        JsonObject key = object(o, "key");
        List<String> rows = new ArrayList<>();
        for (JsonElement e : array(o, "pattern")) rows.add(e.isJsonPrimitive() ? e.getAsString() : "");
        if (rows.isEmpty() || rows.size() > 3) throw new BadRecipe("recipes.bad.pattern");
        int width = rows.get(0).length();
        if (width < 1 || width > 3) throw new BadRecipe("recipes.bad.pattern");
        for (String row : rows) if (row.length() != width) throw new BadRecipe("recipes.bad.pattern");
        Stuff[] cells = new Stuff[width * rows.size()];
        boolean any = false;
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < width; c++) {
                char ch = rows.get(r).charAt(c);
                if (ch == ' ') continue;
                if (!key.has(String.valueOf(ch))) throw new BadRecipe("recipes.bad.key", String.valueOf(ch));
                cells[r * width + c] = stuff(key.get(String.valueOf(ch)));
                any = true;
            }
        }
        if (!any) throw new BadRecipe("recipes.bad.pattern");
        return RecipeData.shaped(name, width, rows.size(), cells, result);
    }

    private static Stuff stuff(JsonElement e) throws BadRecipe {
        if (!e.isJsonObject()) throw new BadRecipe("recipes.bad.item");
        JsonObject o = e.getAsJsonObject();
        return new Stuff(string(o, "item"), integer(o, "meta", 0), o.has("potion") ? string(o, "potion") : null);
    }

    private static Product product(JsonObject o) throws BadRecipe {
        int count = integer(o, "count", 1);
        if (count < 1 || count > 64) throw new BadRecipe("recipes.bad.count", count);
        String nbt = o.has("nbt") && o.get("nbt").isJsonPrimitive() ? o.get("nbt").getAsString() : null;
        return new Product(string(o, "item"), integer(o, "meta", 0), count, nbt);
    }

    private static JsonObject object(JsonObject o, String field) throws BadRecipe {
        if (!o.has(field) || !o.get(field).isJsonObject()) throw new BadRecipe("recipes.bad.field", field);
        return o.getAsJsonObject(field);
    }

    private static JsonArray array(JsonObject o, String field) throws BadRecipe {
        if (!o.has(field) || !o.get(field).isJsonArray()) throw new BadRecipe("recipes.bad.field", field);
        return o.getAsJsonArray(field);
    }

    private static String string(JsonObject o, String field) throws BadRecipe {
        if (!o.has(field) || !o.get(field).isJsonPrimitive()) throw new BadRecipe("recipes.bad.field", field);
        String s = o.get(field).getAsString();
        if (s.isEmpty()) throw new BadRecipe("recipes.bad.field", field);
        return s;
    }

    private static int integer(JsonObject o, String field, int fallback) throws BadRecipe {
        if (!o.has(field)) return fallback;
        try {
            return o.get(field).getAsInt();
        } catch (RuntimeException e) {
            throw new BadRecipe("recipes.bad.field", field);
        }
    }
}
