package com.useleess.carpet.server;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Carpet rule {@code antiNoFall}: a client that says "on the ground" while nothing is under its feet (a no-fall cheat)
 * is not believed, so its fall keeps counting and it lands with the damage it earned. Game-free: the rule and the look
 * under the player's feet come in as suppliers. A failure is reported once, the check switches itself off and the
 * client's word counts again (vanilla).
 */
final class FallCheck {
    static final String RULE = "antiNoFall";
    private final BooleanSupplier ruleOn;
    private final Consumer<Throwable> failed;
    private boolean off;

    FallCheck(BooleanSupplier ruleOn, Consumer<Throwable> failed) {
        this.ruleOn = ruleOn;
        this.failed = failed;
    }

    /** What the server believes: "in the air" is always believed, "on the ground" only with something below. */
    boolean onGround(boolean claimed, BooleanSupplier somethingBelow) {
        if (!claimed || off) return claimed;
        try {
            return !ruleOn.getAsBoolean() || somethingBelow.getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            off = true;
            failed.accept(e);
            return claimed;
        }
    }
}
