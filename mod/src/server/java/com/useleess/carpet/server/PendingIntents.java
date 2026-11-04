package com.useleess.carpet.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One player's placement requests waiting for their click (game-free): one per spot, the oldest goes first, gone after 2 seconds. */
final class PendingIntents {
    static final long LIFETIME_NANOS = 2_000_000_000L;
    static final int MAX = 64;

    private static final class Waiting {
        final LuProtocol.Intent intent;
        final long time;

        Waiting(LuProtocol.Intent intent, long time) {
            this.intent = intent;
            this.time = time;
        }
    }

    private final Map<List<Integer>, Waiting> waiting = new LinkedHashMap<>();

    private static List<Integer> spot(LuProtocol.Intent intent) {
        return Arrays.asList(intent.x, intent.y, intent.z);
    }

    void add(LuProtocol.Intent intent, long now) {
        waiting.remove(spot(intent));
        live(now);
        if (waiting.size() >= MAX) {
            Iterator<Waiting> oldest = waiting.values().iterator();
            oldest.next();
            oldest.remove();
        }
        waiting.put(spot(intent), new Waiting(intent, now));
    }

    void remove(LuProtocol.Intent intent) {
        waiting.remove(spot(intent));
    }

    List<LuProtocol.Intent> live(long now) {
        waiting.values().removeIf(w -> now - w.time > LIFETIME_NANOS);
        List<LuProtocol.Intent> out = new ArrayList<>();
        for (Waiting w : waiting.values()) out.add(w.intent);
        return out;
    }
}
