package com.useleess.carpet.server;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * The one recipe we register with Minecraft: it answers for every saved recipe, asking the {@link RecipeStore} at
 * match time, so saving and deleting take effect at once and the rule {@code customRecipes} off simply makes it
 * match nothing. Vanilla clients are served by the server (the result slot); no recipe book entry. Minecraft calls it
 * directly while crafting, so a failure in it switches the feature off (see {@link Guard}) instead of reaching the game.
 * The interface methods only Forge gives a default body are written out: on the server they are abstract.
 */
public final class CustomRecipes implements IRecipe {
    private static final ResourceLocation KEY = new ResourceLocation("useleess_carpet", "custom_recipes");
    private ResourceLocation name;

    /** Registers the recipe once (Minecraft numbers recipes in the order they come). */
    static void register() {
        if (CraftingManager.REGISTRY.containsKey(KEY)) return;
        int next = 0;
        for (IRecipe r : CraftingManager.REGISTRY) next = Math.max(next, CraftingManager.REGISTRY.getIDForObject(r) + 1);
        CraftingManager.REGISTRY.register(next, KEY, new CustomRecipes());
    }

    private static RecipeData find(InventoryCrafting inv) {
        RecipeData[] found = new RecipeData[1];
        Guard.run("craft", () -> {
            RecipeStore store = RecipeStore.current;
            found[0] = store == null || !Rule.on() ? null : store.find(inv);
        });
        return found[0];
    }

    @Override public boolean matches(InventoryCrafting inv, World world) {
        return find(inv) != null;
    }

    @Override public ItemStack getCraftingResult(InventoryCrafting inv) {
        RecipeData found = find(inv);
        if (found == null) return ItemStack.EMPTY;
        try {
            return ItemCodec.stack(found.result);
        } catch (BadRecipe e) {
            Guard.LOG.warn(ServerText.get("recipes.file_skipped", found.name, ServerText.get(e.key, e.args)));
            return ItemStack.EMPTY;
        }
    }

    /** What stays in the grid after crafting: like vanilla recipes, an item's container (a milk bucket leaves a bucket). */
    @Override public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        NonNullList<ItemStack> left = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < left.size(); i++) {
            Item item = inv.getStackInSlot(i).getItem();
            if (item.hasContainerItem()) left.set(i, new ItemStack(item.getContainerItem()));
        }
        return left;
    }

    @Override public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override public String getGroup() {
        return "";
    }

    @Override public boolean canFit(int width, int height) {
        return true;
    }

    @Override public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override public boolean isDynamic() {
        return true;
    }

    // Forge's IRecipe also asks for a registry name; the server we run on has none of that, so these are plain methods.
    public IRecipe setRegistryName(ResourceLocation newName) {
        name = newName;
        return this;
    }

    public ResourceLocation getRegistryName() {
        return name;
    }

    public Class<IRecipe> getRegistryType() {
        return IRecipe.class;
    }
}
