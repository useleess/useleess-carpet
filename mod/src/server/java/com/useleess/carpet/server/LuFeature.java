package com.useleess.carpet.server;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One Litematica Upgraded feature behind a Carpet rule (game-free). A failure is reported once; it then stays off until the server restarts. */
final class LuFeature {
    private final BooleanSupplier rule;
    private final Consumer<Throwable> report;
    private boolean broken;

    LuFeature(BooleanSupplier rule, Consumer<Throwable> report) {
        this.rule = rule;
        this.report = report;
    }

    boolean on() {
        if (broken) return false;
        try {
            return rule.getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    void whenOn(Runnable action) {
        if (!on()) return;
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    void fail(Throwable e) {
        if (broken) return;
        broken = true;
        report.accept(e);
    }
}
