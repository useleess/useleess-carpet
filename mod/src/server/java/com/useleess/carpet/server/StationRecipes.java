package com.useleess.carpet.server;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.potion.PotionHelper;

import java.util.function.Function;

/**
 * The saved furnace and brewing-stand recipes, asked at the start of Minecraft's own lookups (see {@link Hooks}):
 * {@code FurnaceRecipes.getSmeltingResult} and {@code PotionHelper.isReagent / hasConversions / doReaction}. Those
 * also decide what a furnace, brewing stand or hopper accepts, so vanilla clients and automation work unchanged.
 * Null / false = not ours, Minecraft's own answer follows. {@link #vanillaSmelt} and {@link #vanillaBrew} ask
 * Minecraft alone (the editor's clash check).
 */
final class StationRecipes {
    private static boolean vanillaOnly;

    private StationRecipes() {}

    private static RecipeData live(String where, Function<RecipeStore, RecipeData> query) {
        return vanillaOnly ? null : RecipeStore.live(where, query);
    }

    static ItemStack smeltResult(ItemStack input) {
        return result(live("smelt", store -> store.findSmelting(ItemCodec.stuff(input), null)));
    }

    static boolean ingredient(ItemStack stack) {
        return live("brew", store -> store.findIngredient(ItemCodec.stuff(stack))) != null;
    }

    static boolean brews(ItemStack bottle, ItemStack ingredient) {
        return live("brew", store -> store.findBrewing(ItemCodec.stuff(ingredient), ItemCodec.stuff(bottle), null)) != null;
    }

    static ItemStack brewResult(ItemStack ingredient, ItemStack bottle) {
        return result(live("brew", store -> store.findBrewing(ItemCodec.stuff(ingredient), ItemCodec.stuff(bottle), null)));
    }

    private static ItemStack result(RecipeData found) {
        if (found == null) return null;
        try {
            return ItemCodec.stack(found.result);
        } catch (BadRecipe e) {
            Guard.LOG.warn(ServerText.get("recipes.file_skipped", found.name, ServerText.get(e.key, e.args)));
            return null;
        }
    }

    /** What Minecraft's own furnace recipes make of the item (empty = nothing). */
    static ItemStack vanillaSmelt(ItemStack input) {
        vanillaOnly = true;
        try {
            return FurnaceRecipes.instance().getSmeltingResult(input);
        } finally {
            vanillaOnly = false;
        }
    }

    /** What Minecraft's own brewing makes of the bottle with that ingredient (empty = nothing). */
    static ItemStack vanillaBrew(ItemStack ingredient, ItemStack bottle) {
        vanillaOnly = true;
        try {
            return PotionHelper.isReagent(ingredient) && PotionHelper.hasConversions(bottle, ingredient) ? PotionHelper.doReaction(ingredient, bottle) : ItemStack.EMPTY;
        } finally {
            vanillaOnly = false;
        }
    }
}
