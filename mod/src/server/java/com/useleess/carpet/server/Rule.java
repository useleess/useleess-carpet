package com.useleess.carpet.server;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/** Our plain Carpet rules: fields RulesMaker adds to carpet.CarpetSettings, read by name. */
final class Rule {
    private static BooleanSupplier customRecipes;

    private Rule() {}

    /** Finds {@code customRecipes}; throws when the server has no such rule (the feature then stays off). */
    static void init() {
        customRecipes = named("customRecipes");
        customRecipes.getAsBoolean();
    }

    static boolean on() {
        return customRecipes.getAsBoolean();
    }

    /** A boolean rule, looked up on first read; a missing rule throws {@link IllegalStateException} when read. */
    static BooleanSupplier named(String name) {
        return new BooleanSupplier() {
            private Field field;

            @Override public boolean getAsBoolean() {
                try {
                    if (field == null) field = Class.forName("carpet.CarpetSettings").getField(name);
                    return field.getBoolean(null);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("rule " + name + " not found", e);
                }
            }
        };
    }
}
