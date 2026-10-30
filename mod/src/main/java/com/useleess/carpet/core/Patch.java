package com.useleess.carpet.core;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The patch shipped inside the launcher: how to turn Mojang's official server jar into the finished server jar,
 * file by file. A file is either taken unchanged from Mojang's jar ({@link Kind#SAME}, {@link Kind#FROM}), Mojang's
 * file plus a {@link Delta} ({@link Kind#DELTA}), or a file that is not Mojang's ({@link Kind#NEW}: Carpet,
 * Kisde, VasTech, Redstone Scope; also stored as a delta, against nothing, so any piece it shares with Mojang's
 * jar is copied from there). Every finished file carries its SHA-1, so a wrong or damaged input never produces a
 * server jar.
 */
public final class Patch {
    private static final byte[] MAGIC = {'U', 'C', 'P', 'A', 'T', 'C', 'H', 1};

    public enum Kind { DIR, SAME, FROM, DELTA, NEW }

    public static final class Entry {
        public final String name;
        public final Kind kind;
        /** FROM: the Mojang file to copy; otherwise empty. */
        public final String source;
        /** DELTA: the delta; NEW: the whole file; otherwise empty. */
        public final byte[] data;
        public final byte[] sha1;

        public Entry(String name, Kind kind, String source, byte[] data, byte[] sha1) {
            this.name = name;
            this.kind = kind;
            this.source = source;
            this.data = data;
            this.sha1 = sha1;
        }
    }

    public final String minecraft;
    public final String vanillaSha1;
    public final List<Entry> entries;

    public Patch(String minecraft, String vanillaSha1, List<Entry> entries) {
        this.minecraft = minecraft;
        this.vanillaSha1 = vanillaSha1;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** One fingerprint of the finished jar's contents (names and SHA-1s in order). */
    public String digest() {
        MessageDigest md = Hashes.sha1();
        for (Entry e : entries) {
            md.update(e.name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(e.sha1);
        }
        return Hashes.hex(md.digest());
    }

    public void write(OutputStream raw) throws IOException {
        DeflaterOutputStream zipped = new DeflaterOutputStream(raw);
        DataOutputStream out = new DataOutputStream(zipped);
        out.write(MAGIC);
        out.writeUTF(minecraft);
        out.writeUTF(vanillaSha1);
        out.writeInt(entries.size());
        for (Entry e : entries) {
            out.writeUTF(e.name);
            out.writeByte(e.kind.ordinal());
            out.writeUTF(e.source);
            out.writeInt(e.data.length);
            out.write(e.data);
            out.write(e.sha1);
        }
        out.flush();
        zipped.finish();
    }

    public static Patch read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new InflaterInputStream(raw));
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new Problem("error.broken_patch");
        String minecraft = in.readUTF();
        String vanillaSha1 = in.readUTF();
        int count = in.readInt();
        List<Entry> entries = new ArrayList<>(count);
        Kind[] kinds = Kind.values();
        for (int i = 0; i < count; i++) {
            String name = in.readUTF();
            int kind = in.readUnsignedByte();
            if (kind >= kinds.length) throw new Problem("error.broken_patch");
            String source = in.readUTF();
            byte[] data = new byte[in.readInt()];
            in.readFully(data);
            byte[] sha1 = new byte[20];
            in.readFully(sha1);
            entries.add(new Entry(name, kinds[kind], source, data, sha1));
        }
        return new Patch(minecraft, vanillaSha1, entries);
    }

    /** Builds the finished jar from Mojang's jar; returns its {@link #digest()}. Fails on any mismatch. */
    public String apply(Path vanillaJar, Path outJar) throws IOException {
        return apply(vanillaJar, outJar, null, null);
    }

    /**
     * The same, plus one extra file {@code stampName} holding {@code stamp} at the end (the launcher marks its built
     * jar with the patch fingerprint so a later start knows it is current); null = none.
     */
    public String apply(Path vanillaJar, Path outJar, String stampName, String stamp) throws IOException {
        String actual = Hashes.sha1Hex(vanillaJar);
        if (!actual.equals(vanillaSha1)) throw new Problem("error.wrong_vanilla", actual, vanillaSha1);
        Map<String, byte[]> vanilla = readAll(vanillaJar);
        byte[] dict = dictionary(vanilla);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outJar))) {
            for (Entry e : entries) {
                byte[] bytes = build(e, vanilla, dict);
                if (!Arrays.equals(Hashes.sha1(bytes), e.sha1)) throw new Problem("error.result_differs", e.name);
                out.putNextEntry(new ZipEntry(e.name));
                out.write(bytes);
                out.closeEntry();
            }
            if (stamp != null) {
                out.putNextEntry(new ZipEntry(stampName));
                out.write(stamp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return digest();
    }

    private static byte[] build(Entry e, Map<String, byte[]> vanilla, byte[] dict) throws IOException {
        switch (e.kind) {
            case DIR: return new byte[0];
            case SAME: return need(vanilla, e.name);
            case FROM: return need(vanilla, e.source);
            case DELTA: return Delta.apply(need(vanilla, e.name), e.data, dict);
            default: return Delta.apply(new byte[0], e.data, dict);
        }
    }

    private static byte[] need(Map<String, byte[]> vanilla, String name) throws IOException {
        byte[] b = vanilla.get(name);
        if (b == null) throw new Problem("error.vanilla_missing_file", name);
        return b;
    }

    /** Every file of Mojang's jar back to back, in jar order: what {@link Delta}'s dictionary copies point into. */
    public static byte[] dictionary(Map<String, byte[]> vanilla) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] b : vanilla.values()) all.write(b, 0, b.length);
        return all.toByteArray();
    }

    /** Every file of a jar, in jar order. */
    public static Map<String, byte[]> readAll(Path jar) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry e : Collections.list(zip.entries())) {
                try (InputStream in = zip.getInputStream(e)) {
                    files.put(e.getName(), readFully(in));
                }
            }
        }
        return files;
    }

    public static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
