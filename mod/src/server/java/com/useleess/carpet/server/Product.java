package com.useleess.carpet.server;

import java.util.Objects;

/** A recipe's result: item, meta, count and the item's NBT as SNBT text (null = none). Game-free. */
public final class Product {
    public final String item;
    public final int meta;
    public final int count;
    public final String nbt;

    public Product(String item, int meta, int count, String nbt) {
        this.item = Objects.requireNonNull(item);
        this.meta = meta;
        this.count = count;
        this.nbt = nbt;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Product)) return false;
        Product p = (Product) o;
        return p.item.equals(item) && p.meta == meta && p.count == count && Objects.equals(p.nbt, nbt);
    }

    @Override public int hashCode() {
        return Objects.hash(item, meta, count, nbt);
    }
}
