package com.useleess.carpet.server;

import java.lang.reflect.Field;

/** The Carpet rule {@code customRecipes} (a field RulesMaker adds to carpet.CarpetSettings; read by name). */
final class Rule {
    private static Field field;

    private Rule() {}

    /** Finds the rule's field; throws when the server has no such rule (the feature then stays off). */
    static void init() {
        try {
            field = Class.forName("carpet.CarpetSettings").getField("customRecipes");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("rule customRecipes not found", e);
        }
    }

    static boolean on() {
        try {
            return field.getBoolean(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
