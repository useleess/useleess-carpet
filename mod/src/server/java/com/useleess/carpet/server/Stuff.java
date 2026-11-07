package com.useleess.carpet.server;

import java.util.Objects;

/**
 * One item kind in a recipe: registry name + meta ({@link #ANY_META} = any meta) + the potion type of a potion,
 * splash / lingering potion or tipped arrow (null = none, or any for an ingredient). Game-free.
 */
public final class Stuff {
    public static final int ANY_META = 32767;
    public final String item;
    public final int meta;
    public final String potion;

    public Stuff(String item, int meta) {
        this(item, meta, null);
    }

    public Stuff(String item, int meta, String potion) {
        this.item = Objects.requireNonNull(item);
        this.meta = meta;
        this.potion = potion;
    }

    /** Whether this recipe ingredient takes that actual item (null = an empty slot: never). */
    public boolean accepts(Stuff actual) {
        return actual != null && item.equals(actual.item) && (meta == ANY_META || meta == actual.meta)
                && (potion == null || potion.equals(actual.potion));
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Stuff)) return false;
        Stuff s = (Stuff) o;
        return s.item.equals(item) && s.meta == meta && Objects.equals(s.potion, potion);
    }

    @Override public int hashCode() {
        return Objects.hash(item, meta, potion);
    }

    @Override public String toString() {
        return item + "/" + meta + (potion == null ? "" : " " + potion);
    }
}
