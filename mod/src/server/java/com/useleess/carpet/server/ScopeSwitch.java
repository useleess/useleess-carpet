package com.useleess.carpet.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keeps Redstone Scope's recording switch ({@code com.redstonescope.RecordingSwitch}) in step with the Carpet rule
 * {@code redstoneScopeLogger}. Both are found by name: a master jar without them logs it once, tells the ops, and
 * nothing else changes.
 */
final class ScopeSwitch {
    private static Field rule;
    private static Method setOn;
    private static Method isOn;
    private static boolean broken;

    private ScopeSwitch() {}

    /** Finds the rule and Redstone Scope's switch; a missing one is told once and the rule then does nothing. */
    static void init() {
        try {
            rule = Class.forName("carpet.CarpetSettings").getField("redstoneScopeLogger");
            setOn = Class.forName("com.redstonescope.RecordingSwitch").getMethod("setOn", boolean.class);
            isOn = Class.forName("com.redstonescope.RecordingSwitch").getMethod("isOn");
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            Guard.LOG.error(ServerText.get("log.scope_switch_missing"), e);
            Guard.notice(ServerText.get("scope.switch_missing"));
        }
    }

    /** Called every tick: passes the rule's value on to Redstone Scope only when it differs from the switch. */
    static void sync() {
        if (broken) return;
        try {
            boolean on = rule.getBoolean(null);
            if ((boolean) isOn.invoke(null) != on) setOn.invoke(null, on);
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            Guard.LOG.error(ServerText.get("log.scope_switch_missing"), e);
            Guard.notice(ServerText.get("scope.switch_missing"));
        }
    }
}
