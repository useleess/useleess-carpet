package com.useleess.carpet.server;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

/** The picker's categories, like the creative inventory's tabs. */
enum Category {
    BUILDING("building"), DECORATION("decoration"), REDSTONE("redstone"), TRANSPORT("transport"), MISC("misc"),
    FOOD("food"), TOOLS("tools"), COMBAT("combat"), BREWING("brewing"), MATERIALS("materials");

    /** Language key suffix: {@code category.<key>}. */
    final String key;

    Category(String key) {
        this.key = key;
    }

    Category next(int by) {
        Category[] all = values();
        return all[(ordinal() + by + all.length) % all.length];
    }

    ItemStack icon() {
        switch (this) {
            case BUILDING: return new ItemStack(Blocks.BRICK_BLOCK);
            case DECORATION: return new ItemStack(Blocks.RED_FLOWER);
            case REDSTONE: return new ItemStack(Items.REDSTONE);
            case TRANSPORT: return new ItemStack(Blocks.RAIL);
            case MISC: return new ItemStack(Items.LAVA_BUCKET);
            case FOOD: return new ItemStack(Items.APPLE);
            case TOOLS: return new ItemStack(Items.IRON_AXE);
            case COMBAT: return new ItemStack(Items.GOLDEN_SWORD);
            case BREWING: return new ItemStack(Items.BREWING_STAND);
            default: return new ItemStack(Items.STICK);
        }
    }

    /** From the item's creative tab when it has one, else by what kind of item it is. */
    static Category of(Item item) {
        CreativeTabs tab = item.getCreativeTab();
        if (tab == CreativeTabs.BUILDING_BLOCKS) return BUILDING;
        if (tab == CreativeTabs.DECORATIONS) return DECORATION;
        if (tab == CreativeTabs.REDSTONE) return REDSTONE;
        if (tab == CreativeTabs.TRANSPORTATION) return TRANSPORT;
        if (tab == CreativeTabs.MISC) return MISC;
        if (tab == CreativeTabs.FOOD) return FOOD;
        if (tab == CreativeTabs.TOOLS) return TOOLS;
        if (tab == CreativeTabs.COMBAT) return COMBAT;
        if (tab == CreativeTabs.BREWING) return BREWING;
        if (tab == CreativeTabs.MATERIALS) return MATERIALS;
        if (item instanceof ItemFood) return FOOD;
        if (item instanceof ItemTool || item instanceof ItemHoe) return TOOLS;
        if (item instanceof ItemSword || item instanceof ItemArmor || item instanceof ItemBow) return COMBAT;
        if (item instanceof ItemPotion) return BREWING;
        if (item instanceof ItemBlock) return BUILDING;
        return MISC;
    }

}
