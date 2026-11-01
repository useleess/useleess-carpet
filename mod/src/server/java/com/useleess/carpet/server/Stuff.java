package com.useleess.carpet.server;

import java.util.Objects;

/** One item kind in a recipe: registry name + meta ({@link #ANY_META} = any meta). Game-free. */
public final class Stuff {
    public static final int ANY_META = 32767;
    public final String item;
    public final int meta;

    public Stuff(String item, int meta) {
        this.item = Objects.requireNonNull(item);
        this.meta = meta;
    }

    /** Whether this recipe ingredient takes that actual item. */
    public boolean accepts(Stuff actual) {
        return item.equals(actual.item) && (meta == ANY_META || meta == actual.meta);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Stuff && ((Stuff) o).item.equals(item) && ((Stuff) o).meta == meta;
    }

    @Override public int hashCode() {
        return item.hashCode() * 31 + meta;
    }

    @Override public String toString() {
        return item + "/" + meta;
    }
}
