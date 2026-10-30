package com.useleess.carpet.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A binary diff of one file against its original: a list of "copy N bytes from the original at offset X" and
 * "insert these new bytes" steps, plus "copy N bytes from Mojang's whole jar at offset X" (the dictionary: every file
 * of Mojang's jar back to back, so a piece shared with ANY Mojang file is copied, not shipped). Only the inserted
 * bytes are new; everything copied stays in Mojang's jar, so a delta carries the edit, not Mojang's code.
 */
public final class Delta {
    /** Shortest run worth copying instead of inserting. */
    static final int MIN_MATCH = 12;
    private static final int MAX_CANDIDATES = 64;
    private static final int OP_COPY = 'C';
    private static final int OP_INSERT = 'I';
    private static final int OP_DICT = 'G';
    /** The dictionary is indexed every DICT_STRIDE bytes by DICT_WIDTH bytes: any shared run of 64+ is found. */
    public static final int DICT_STRIDE = 16;
    public static final int DICT_WIDTH = 48;

    private Delta() {}

    public static byte[] make(byte[] base, byte[] target) {
        return make(base, target, null, null);
    }

    /** {@code dict} + {@code dictIndex} (from {@link #dictIndex}) may be null: then only {@code base} is copied from. */
    public static byte[] make(byte[] base, byte[] target, byte[] dict, Index dictIndex) {
        Index index = new Index(base, MIN_MATCH, 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, target.length);
        int i = 0;
        int literalStart = 0;
        while (i + MIN_MATCH <= target.length) {
            int best = 0;
            int bestPos = -1;
            int steps = 0;
            for (int k = index.first(target, i); k >= 0 && steps < MAX_CANDIDATES; k = index.next(k), steps++) {
                int len = matchLength(base, index.pos(k), target, i);
                if (len > best) {
                    best = len;
                    bestPos = index.pos(k);
                }
            }
            byte[] from = base;
            int op = OP_COPY;
            if (dictIndex != null && i + DICT_WIDTH <= target.length) {
                steps = 0;
                for (int k = dictIndex.first(target, i); k >= 0 && steps < MAX_CANDIDATES; k = dictIndex.next(k), steps++) {
                    int len = matchLength(dict, dictIndex.pos(k), target, i);
                    if (len >= DICT_WIDTH && len > best) {
                        best = len;
                        bestPos = dictIndex.pos(k);
                        from = dict;
                        op = OP_DICT;
                    }
                }
            }
            if (best < MIN_MATCH) {
                i++;
                continue;
            }
            int back = 0;
            while (i - back > literalStart && bestPos - back > 0 && from[bestPos - back - 1] == target[i - back - 1]) {
                back++;
            }
            insert(out, target, literalStart, i - back);
            out.write(op);
            writeVarInt(out, bestPos - back);
            writeVarInt(out, best + back);
            i += best;
            literalStart = i;
        }
        insert(out, target, literalStart, target.length);
        return out.toByteArray();
    }

    public static byte[] apply(byte[] base, byte[] delta) throws IOException {
        return apply(base, delta, new byte[0]);
    }

    public static byte[] apply(byte[] base, byte[] delta, byte[] dict) throws IOException {
        int[] pos = {0};
        int length = readVarInt(delta, pos);
        byte[] result = new byte[length];
        int at = 0;
        while (pos[0] < delta.length) {
            int op = delta[pos[0]++];
            if (op == OP_COPY || op == OP_DICT) {
                byte[] source = op == OP_COPY ? base : dict;
                int from = readVarInt(delta, pos);
                int len = readVarInt(delta, pos);
                check(from >= 0 && len >= 0 && from + len <= source.length && at + len <= length);
                System.arraycopy(source, from, result, at, len);
                at += len;
            } else if (op == OP_INSERT) {
                int len = readVarInt(delta, pos);
                check(len >= 0 && pos[0] + len <= delta.length && at + len <= length);
                System.arraycopy(delta, pos[0], result, at, len);
                pos[0] += len;
                at += len;
            } else {
                check(false);
            }
        }
        check(at == length);
        return result;
    }

    /** The new bytes a delta inserts, run by run (what a delta adds that is not in the original). */
    public static byte[][] insertedRuns(byte[] delta) throws IOException {
        int[] pos = {0};
        readVarInt(delta, pos);
        java.util.List<byte[]> runs = new java.util.ArrayList<>();
        while (pos[0] < delta.length) {
            int op = delta[pos[0]++];
            if (op == OP_COPY || op == OP_DICT) {
                readVarInt(delta, pos);
                readVarInt(delta, pos);
            } else if (op == OP_INSERT) {
                int len = readVarInt(delta, pos);
                runs.add(Arrays.copyOfRange(delta, pos[0], pos[0] + len));
                pos[0] += len;
            } else {
                check(false);
            }
        }
        return runs.toArray(new byte[0][]);
    }

    private static void insert(ByteArrayOutputStream out, byte[] target, int from, int to) {
        if (to <= from) return;
        out.write(OP_INSERT);
        writeVarInt(out, to - from);
        out.write(target, from, to - from);
    }

    private static int matchLength(byte[] a, int ai, byte[] b, int bi) {
        int n = 0;
        while (ai + n < a.length && bi + n < b.length && a[ai + n] == b[bi + n]) n++;
        return n;
    }

    private static void check(boolean ok) throws IOException {
        if (!ok) throw new Problem("error.broken_patch");
    }

    static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(byte[] data, int[] pos) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            check(pos[0] < data.length);
            int b = data[pos[0]++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new Problem("error.broken_patch");
    }

    /** The dictionary index: Mojang's whole jar every {@link #DICT_STRIDE} bytes. */
    public static Index dictIndex(byte[] dict) {
        return new Index(dict, DICT_WIDTH, DICT_STRIDE);
    }

    /**
     * Every {@code stride}-th position of a byte array, findable by the {@code width} bytes that start there.
     * Candidates are entry numbers k: {@link #pos(int)} is the position, {@link #next(int)} the next candidate.
     */
    public static final class Index {
        private final int width;
        private final int stride;
        private final int[] head;
        private final int[] next;
        private final int mask;

        public Index(byte[] data, int width, int stride) {
            this.width = width;
            this.stride = stride;
            int count = Math.max(0, (data.length - width) / stride + 1);
            int size = Integer.highestOneBit(Math.max(16, count) * 2);
            head = new int[size];
            Arrays.fill(head, -1);
            mask = size - 1;
            next = new int[count];
            for (int k = 0; k < count; k++) {
                int h = hash(data, k * stride) & mask;
                next[k] = head[h];
                head[h] = k;
            }
        }

        public int first(byte[] other, int at) {
            return head[hash(other, at) & mask];
        }

        public int next(int k) {
            return next[k];
        }

        public int pos(int k) {
            return k * stride;
        }

        private int hash(byte[] d, int at) {
            int h = 0;
            for (int k = 0; k < width; k++) h = h * 31 + d[at + k];
            return h ^ (h >>> 16);
        }
    }
}
