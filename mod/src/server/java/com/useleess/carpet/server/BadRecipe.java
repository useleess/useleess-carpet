package com.useleess.carpet.server;

/** A recipe file that cannot be used: the language key of the reason and its values. */
public final class BadRecipe extends Exception {
    public final String key;
    public final transient Object[] args;

    public BadRecipe(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args;
    }
}
