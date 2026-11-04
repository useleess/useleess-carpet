package com.useleess.carpet.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The switch-off path: whatever goes wrong, the game keeps running, the failure is told once and the feature stays off. */
class LuFeatureTest {
    private final List<Throwable> reported = new ArrayList<>();
    private boolean rule = true;
    private int runs;

    private LuFeature feature() {
        return new LuFeature(() -> rule, reported::add);
    }

    @Test void followsItsRule() {
        LuFeature f = feature();
        f.whenOn(() -> runs++);
        rule = false;
        f.whenOn(() -> runs++);
        assertFalse(f.on());
        assertEquals(1, runs);
        assertTrue(reported.isEmpty());
    }

    @Test void aFailingActionSwitchesItOffOnce() {
        LuFeature f = feature();
        f.whenOn(() -> { throw new IllegalStateException("bad"); });
        assertFalse(f.on());
        f.whenOn(() -> runs++);
        f.fail(new IllegalStateException("again"));
        assertEquals(0, runs);
        assertEquals(1, reported.size());
    }

    @Test void aMissingGameMethodSwitchesItOffToo() {
        LuFeature f = feature();
        f.whenOn(() -> { throw new NoSuchMethodError("x"); });
        assertFalse(f.on());
        assertEquals(1, reported.size());
    }

    @Test void aMissingRuleCountsAsOff() {
        LuFeature f = new LuFeature(() -> { throw new IllegalStateException("rule not found"); }, reported::add);
        assertFalse(f.on());
        assertEquals(1, reported.size());
    }
}
