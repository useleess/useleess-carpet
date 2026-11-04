package com.useleess.carpet.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PendingIntentsTest {
    private static LuProtocol.Intent at(int x, String state) {
        return new LuProtocol.Intent(x, 4, 0, state);
    }

    @Test void aRequestWaitsItsLifetimeThenGoes() {
        PendingIntents p = new PendingIntents();
        p.add(at(1, "a"), 0);
        assertEquals(1, p.live(PendingIntents.LIFETIME_NANOS).size());
        assertTrue(p.live(PendingIntents.LIFETIME_NANOS + 1).isEmpty());
    }

    @Test void theNewestRequestForASpotWins() {
        PendingIntents p = new PendingIntents();
        p.add(at(1, "old"), 0);
        p.add(at(1, "new"), 1);
        List<LuProtocol.Intent> live = p.live(1);
        assertEquals(1, live.size());
        assertEquals("new", live.get(0).block);
    }

    @Test void theOldestGoesWhenFull() {
        PendingIntents p = new PendingIntents();
        for (int i = 0; i <= PendingIntents.MAX; i++) p.add(at(i, "b"), i);
        List<LuProtocol.Intent> live = p.live(PendingIntents.MAX);
        assertEquals(PendingIntents.MAX, live.size());
        assertEquals(1, live.get(0).x);
    }

    @Test void aHandledRequestIsGone() {
        PendingIntents p = new PendingIntents();
        LuProtocol.Intent i = at(1, "a");
        p.add(i, 0);
        p.remove(i);
        assertTrue(p.live(0).isEmpty());
    }
}
