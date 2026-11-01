package com.useleess.carpet.server;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Items as recipe data (registry name + meta, NBT as SNBT) and back. */
final class ItemCodec {
    private ItemCodec() {}

    static boolean exists(String itemName) {
        return Item.REGISTRY.containsKey(new ResourceLocation(itemName));
    }

    /** The recipe ingredient for a stack, or null for an empty one. */
    static Stuff stuff(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return new Stuff(Item.REGISTRY.getNameForObject(stack.getItem()).toString(), stack.getMetadata());
    }

    static Stuff[] grid(InventoryCrafting inv) {
        Stuff[] out = new Stuff[inv.getSizeInventory()];
        for (int i = 0; i < out.length; i++) out[i] = stuff(inv.getStackInSlot(i));
        return out;
    }

    static Product product(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return new Product(Item.REGISTRY.getNameForObject(stack.getItem()).toString(), stack.getMetadata(), stack.getCount(),
                tag == null || tag.hasNoTags() ? null : tag.toString());
    }

    /** One item of that kind (a wildcard meta gives meta 0). */
    static ItemStack stack(Stuff s) {
        if (!exists(s.item)) return ItemStack.EMPTY;
        return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(s.item)), 1, s.meta == Stuff.ANY_META ? 0 : s.meta);
    }

    static ItemStack stack(Product p) throws BadRecipe {
        if (!exists(p.item)) throw new BadRecipe("recipes.bad.item_unknown", p.item);
        ItemStack stack = new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(p.item)), p.count, p.meta);
        if (p.nbt != null) {
            try {
                stack.setTagCompound(JsonToNBT.getTagFromJson(p.nbt));
            } catch (NBTException e) {
                throw new BadRecipe("recipes.bad.nbt", e.getMessage());
            }
        }
        return stack;
    }

    /** Throws when a file names an item this server does not have, or an NBT it cannot read. */
    static void check(RecipeData r) throws BadRecipe {
        stack(r.result);
        for (Stuff s : r.ingredients()) if (!exists(s.item)) throw new BadRecipe("recipes.bad.item_unknown", s.item);
    }
}
