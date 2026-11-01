package com.useleess.carpet.server;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Every registered item with its variants (meta), built once on first use, filtered for the picker. */
final class Catalogue {
    static final class Entry {
        final ItemStack stack;
        final Category category;
        final String searchName;

        Entry(ItemStack stack) {
            this.stack = stack;
            this.category = Category.of(stack.getItem());
            String registryName = ItemCodec.stuff(stack).item;
            this.searchName = registryName.substring(registryName.indexOf(':') + 1);
        }
    }

    private static List<Entry> all;

    private Catalogue() {}

    private static List<Entry> all() {
        if (all != null) return all;
        List<Entry> out = new ArrayList<>();
        Set<Stuff> seen = new HashSet<>();
        for (Item item : Item.REGISTRY) {
            if (item == Items.AIR) continue;
            NonNullList<ItemStack> variants = NonNullList.create();
            try {
                item.getSubItems(CreativeTabs.SEARCH, variants);
            } catch (RuntimeException e) {
                // an item from elsewhere whose variant list fails: its plain item is still offered
                Guard.LOG.warn(ServerText.get("log.catalogue_item_failed", String.valueOf(Item.REGISTRY.getNameForObject(item))), e);
                variants.clear();
            }
            if (variants.isEmpty()) variants.add(new ItemStack(item));
            for (ItemStack s : variants) {
                if (s.isEmpty()) continue;
                ItemStack one = s.copy();
                one.setCount(1);
                if (seen.add(ItemCodec.stuff(one))) out.add(new Entry(one));
            }
        }
        all = out;
        return all;
    }

    /** The items whose registry name holds every word of the search text, from every category; a blank search lists the category. */
    static List<Entry> filter(Category category, String search) {
        String[] words = search.toLowerCase(Locale.ROOT).trim().split("[ _]+");
        boolean searching = !search.trim().isEmpty();
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (!searching && e.category != category) continue;
            boolean ok = true;
            for (String w : words) ok &= w.isEmpty() || e.searchName.contains(w);
            if (ok) out.add(e);
        }
        return out;
    }

    /** The category an item is listed in. */
    static Category categoryOf(ItemStack stack) {
        for (Entry e : all()) if (ItemCodec.stuff(e.stack).equals(ItemCodec.stuff(stack))) return e.category;
        return Category.of(stack.getItem());
    }
}
