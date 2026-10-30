package com.useleess.carpet.tools;

import com.useleess.carpet.core.Delta;
import com.useleess.carpet.core.Patch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Proves the patch ships nothing of Mojang's: no run of {@value #RUN} or more bytes that the patch inserts (the only
 * bytes it carries itself) also appears anywhere in Mojang's server jar. Short runs are allowed: tiny common pieces
 * (a type name, a few instructions) appear in any Java class. Independent of how the patch was made: it reads the
 * finished patch's inserted bytes and searches all of Mojang's files for them.
 */
public final class LeakCheck {
    public static final int RUN = 64;

    private LeakCheck() {}

    public static final class Result {
        public final List<String> hits = new ArrayList<>();
        public long shippedBytes;

        public boolean clean() {
            return hits.isEmpty();
        }

        @Override public String toString() {
            return "shipped " + shippedBytes + " B, Mojang runs of " + RUN + "+ B found: " + hits.size()
                    + (hits.isEmpty() ? "" : " e.g. " + hits.subList(0, Math.min(10, hits.size())));
        }
    }

    public static Result run(Patch patch, Map<String, byte[]> vanilla) throws IOException {
        byte[] mojang = Patch.dictionary(vanilla);
        // every 16th position by 48 bytes: any shared run of 64+ covers one indexed window completely
        Delta.Index index = Delta.dictIndex(mojang);
        Result r = new Result();
        for (Patch.Entry e : patch.entries) {
            if (e.kind != Patch.Kind.NEW && e.kind != Patch.Kind.DELTA) continue;
            for (byte[] run : Delta.insertedRuns(e.data)) check(e.name, run, mojang, index, r);
        }
        return r;
    }

    private static void check(String name, byte[] data, byte[] mojang, Delta.Index index, Result r) {
        r.shippedBytes += data.length;
        for (int i = 0; i + Delta.DICT_WIDTH <= data.length; i++) {
            for (int k = index.first(data, i); k >= 0; k = index.next(k)) {
                int p = index.pos(k);
                int f = 0;
                while (p + f < mojang.length && i + f < data.length && mojang[p + f] == data[i + f]) f++;
                if (f < Delta.DICT_WIDTH) continue;
                int b = 0;
                while (p - b > 0 && i - b > 0 && mojang[p - b - 1] == data[i - b - 1]) b++;
                if (f + b >= RUN) {
                    r.hits.add(name + "@" + (i - b) + "(" + (f + b) + " B)");
                    return;
                }
            }
        }
    }
}
