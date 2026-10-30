package com.useleess.carpet;

import com.useleess.carpet.core.Delta;
import com.useleess.carpet.core.Hashes;
import com.useleess.carpet.core.Patch;
import com.useleess.carpet.core.Problem;
import com.useleess.carpet.tools.LeakCheck;
import com.useleess.carpet.tools.PatchMaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** The delta and the patch format on small made-up jars (no game needed). */
class PatchTest {
    @TempDir Path dir;

    static byte[] random(long seed, int n) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    static Path jar(Path file, Map<String, byte[]> files) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return file;
    }

    @Test void deltaRoundTrips() throws IOException {
        byte[] base = random(1, 5000);
        byte[] edited = base.clone();
        edited[100] ^= 1;
        byte[] grown = new byte[6000];
        System.arraycopy(base, 0, grown, 0, 3000);
        System.arraycopy(random(2, 1000), 0, grown, 3000, 1000);
        System.arraycopy(base, 3000, grown, 4000, 2000);
        for (byte[] target : new byte[][]{base, edited, grown, new byte[0], random(3, 300), Arrays.copyOf(base, 11)}) {
            assertArrayEquals(target, Delta.apply(base, Delta.make(base, target)));
        }
        assertArrayEquals(new byte[0], Delta.apply(new byte[0], Delta.make(new byte[0], new byte[0])));
    }

    @Test void deltaOfSmallEditIsSmall() throws IOException {
        byte[] base = random(4, 20000);
        byte[] edited = base.clone();
        edited[5000] ^= 1;
        assertTrue(Delta.make(base, edited).length < 40);
        int inserted = 0;
        for (byte[] run : Delta.insertedRuns(Delta.make(base, edited))) inserted += run.length;
        assertTrue(inserted <= 1, "only the changed byte is new");
    }

    @Test void deltaCopiesFromTheDictionary() throws IOException {
        byte[] dict = random(5, 10000);
        byte[] target = new byte[700];
        System.arraycopy(random(6, 300), 0, target, 0, 300);
        System.arraycopy(dict, 1234, target, 300, 400);
        byte[] d = Delta.make(new byte[0], target, dict, Delta.dictIndex(dict));
        assertArrayEquals(target, Delta.apply(new byte[0], d, dict));
        int inserted = 0;
        for (byte[] run : Delta.insertedRuns(d)) inserted += run.length;
        assertTrue(inserted <= 300 + 16, "the dictionary part is copied, not inserted: " + inserted);
    }

    @Test void brokenDeltaIsRefused() {
        byte[] base = random(7, 100);
        byte[] d = Delta.make(base, random(8, 50));
        assertThrows(IOException.class, () -> Delta.apply(base, Arrays.copyOf(d, d.length - 3)));
        assertThrows(IOException.class, () -> Delta.apply(base, new byte[]{5, 'X'}));
    }

    Map<String, byte[]> vanillaFiles() {
        Map<String, byte[]> v = new LinkedHashMap<>();
        v.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes());
        v.put("a.class", random(10, 3000));
        v.put("b.class", random(11, 3000));
        v.put("gone.class", random(12, 500));
        v.put("lib/x.class", random(13, 800));
        return v;
    }

    Map<String, byte[]> masterFiles(Map<String, byte[]> v) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("META-INF/MANIFEST.MF", v.get("META-INF/MANIFEST.MF"));
        m.put("carpet/", new byte[0]);
        byte[] b = v.get("b.class").clone();
        b[10] = 42;
        m.put("b.class", b);
        m.put("a.class", v.get("a.class"));
        m.put("copy-of-x.class", v.get("lib/x.class"));
        m.put("carpet/New.class", random(14, 900));
        m.put("lib/x.class", v.get("lib/x.class"));
        return m;
    }

    @Test void patchRebuildsTheMasterJarExactly() throws IOException {
        Map<String, byte[]> v = vanillaFiles();
        Map<String, byte[]> m = masterFiles(v);
        Path vanilla = jar(dir.resolve("v.jar"), v);
        Path master = jar(dir.resolve("m.jar"), m);
        Patch p = PatchMaker.make("1.12.2", vanilla, master);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        p.write(bytes);
        Patch read = Patch.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(p.digest(), read.digest());
        Path out = dir.resolve("out.jar");
        assertEquals(p.digest(), read.apply(vanilla, out));
        Map<String, byte[]> got = Patch.readAll(out);
        assertEquals(new ArrayList<>(m.keySet()), new ArrayList<>(got.keySet()), "same files, same order");
        for (String name : m.keySet()) assertArrayEquals(m.get(name), got.get(name), name);
        Path written = dir.resolve("p.patch");
        try (OutputStream o = Files.newOutputStream(written)) {
            p.write(o);
        }
        PatchMaker.verify(written, vanilla, master);
    }

    @Test void patchKindsShipOnlyWhatIsNew() throws IOException {
        Map<String, byte[]> v = vanillaFiles();
        Path vanilla = jar(dir.resolve("v.jar"), v);
        Patch p = PatchMaker.make("1.12.2", vanilla, jar(dir.resolve("m.jar"), masterFiles(v)));
        Map<String, Patch.Kind> kinds = new LinkedHashMap<>();
        for (Patch.Entry e : p.entries) kinds.put(e.name, e.kind);
        assertEquals(Patch.Kind.SAME, kinds.get("a.class"));
        assertEquals(Patch.Kind.DELTA, kinds.get("b.class"));
        assertEquals(Patch.Kind.FROM, kinds.get("copy-of-x.class"));
        assertEquals(Patch.Kind.NEW, kinds.get("carpet/New.class"));
        assertEquals(Patch.Kind.DIR, kinds.get("carpet/"));
        assertFalse(kinds.containsKey("gone.class"));
        assertTrue(LeakCheck.run(p, v).clean());
    }

    @Test void wrongMojangJarIsRefused() throws IOException {
        Map<String, byte[]> v = vanillaFiles();
        Path vanilla = jar(dir.resolve("v.jar"), v);
        Patch p = PatchMaker.make("1.12.2", vanilla, jar(dir.resolve("m.jar"), masterFiles(v)));
        v.put("a.class", random(99, 3000));
        Path other = jar(dir.resolve("other.jar"), v);
        Problem e = assertThrows(Problem.class, () -> p.apply(other, dir.resolve("out.jar")));
        assertEquals("error.wrong_vanilla", e.key);
    }

    @Test void damagedPatchFileIsRefused() throws IOException {
        Map<String, byte[]> v = vanillaFiles();
        Path vanilla = jar(dir.resolve("v.jar"), v);
        Patch p = PatchMaker.make("1.12.2", vanilla, jar(dir.resolve("m.jar"), masterFiles(v)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        p.write(bytes);
        byte[] cut = Arrays.copyOf(bytes.toByteArray(), bytes.size() / 2);
        assertThrows(IOException.class, () -> Patch.read(new ByteArrayInputStream(cut)));
        assertThrows(IOException.class, () -> Patch.read(new ByteArrayInputStream("not a patch".getBytes())));
    }

    @Test void wrongResultIsCaughtBySha1() throws IOException {
        Map<String, byte[]> v = vanillaFiles();
        Path vanilla = jar(dir.resolve("v.jar"), v);
        Patch p = PatchMaker.make("1.12.2", vanilla, jar(dir.resolve("m.jar"), masterFiles(v)));
        java.util.List<Patch.Entry> entries = new ArrayList<>(p.entries);
        Patch.Entry e = entries.get(0);
        entries.set(0, new Patch.Entry(e.name, e.kind, e.source, e.data, Hashes.sha1(new byte[]{1})));
        Problem x = assertThrows(Problem.class, () -> new Patch(p.minecraft, p.vanillaSha1, entries).apply(vanilla, dir.resolve("o.jar")));
        assertEquals("error.result_differs", x.key);
    }

    @Test void leakCheckCatchesShippedMojangBytes() throws IOException {
        Map<String, byte[]> v = vanillaFiles();
        byte[] leaked = new byte[400];
        System.arraycopy(random(20, 200), 0, leaked, 0, 200);
        System.arraycopy(v.get("a.class"), 500, leaked, 200, 100);
        System.arraycopy(random(21, 100), 0, leaked, 300, 100);
        Path vanilla = jar(dir.resolve("v.jar"), v);
        // a patch that inserts the bytes itself (as an old format would): must be flagged
        Patch bad = new Patch("1.12.2", Hashes.sha1Hex(vanilla), java.util.Collections.singletonList(
                new Patch.Entry("carpet/Leak.class", Patch.Kind.NEW, "", Delta.make(new byte[0], leaked), Hashes.sha1(leaked))));
        LeakCheck.Result r = LeakCheck.run(bad, v);
        assertFalse(r.clean(), r.toString());
        // the real maker copies that piece from Mojang's jar instead: clean
        Map<String, byte[]> m = new LinkedHashMap<>(v);
        m.put("carpet/Leak.class", leaked);
        assertTrue(LeakCheck.run(PatchMaker.make("1.12.2", vanilla, jar(dir.resolve("m.jar"), m)), v).clean());
    }

    @Test void leakCheckAllowsShortCommonPieces() throws IOException {
        Map<String, byte[]> v = vanillaFiles();
        byte[] data = new byte[300];
        System.arraycopy(random(30, 300), 0, data, 0, 300);
        System.arraycopy(v.get("a.class"), 100, data, 50, LeakCheck.RUN - 1);
        Path vanilla = jar(dir.resolve("v.jar"), v);
        Patch p = new Patch("1.12.2", Hashes.sha1Hex(vanilla), java.util.Collections.singletonList(
                new Patch.Entry("carpet/Short.class", Patch.Kind.NEW, "", Delta.make(new byte[0], data), Hashes.sha1(data))));
        assertTrue(LeakCheck.run(p, v).clean());
    }
}
