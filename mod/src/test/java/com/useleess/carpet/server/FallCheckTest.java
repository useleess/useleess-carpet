package com.useleess.carpet.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Rule antiNoFall without the game: what the server believes, and the switch-off after a failure. */
class FallCheckTest {
    private final List<Throwable> reported = new ArrayList<>();
    private int looks;

    private BooleanSupplier below(boolean something) {
        return () -> {
            looks++;
            return something;
        };
    }

    @Test void ruleOffBelievesTheClient() {
        FallCheck check = new FallCheck(() -> false, reported::add);
        assertTrue(check.onGround(true, below(false)));
        assertFalse(check.onGround(false, below(true)));
        assertEquals(0, looks);
    }

    @Test void ruleOnDoesNotBelieveGroundInTheAir() {
        FallCheck check = new FallCheck(() -> true, reported::add);
        assertFalse(check.onGround(true, below(false)));
        assertTrue(check.onGround(true, below(true)));
    }

    @Test void inTheAirIsAlwaysBelievedWithoutLooking() {
        FallCheck check = new FallCheck(() -> true, reported::add);
        assertFalse(check.onGround(false, below(true)));
        assertEquals(0, looks);
    }

    @Test void aFailedLookIsReportedOnceAndSwitchesTheCheckOff() {
        FallCheck check = new FallCheck(() -> true, reported::add);
        BooleanSupplier broken = () -> {
            looks++;
            throw new IllegalStateException("no world");
        };
        assertTrue(check.onGround(true, broken));
        assertTrue(check.onGround(true, broken));
        assertTrue(check.onGround(true, below(false)), "off = the client's word");
        assertEquals(1, looks);
        assertEquals(1, reported.size());
    }

    @Test void aServerWithoutTheRuleKeepsVanillaFallDamage() {
        FallCheck check = new FallCheck(Rule.named("antiNoFall"), reported::add);
        assertTrue(check.onGround(true, below(false)));
        assertEquals(1, reported.size());
        assertTrue(reported.get(0).getMessage().contains("antiNoFall"));
        assertTrue(check.onGround(true, below(false)));
        assertEquals(1, reported.size());
    }
}
