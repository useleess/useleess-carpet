package com.useleess.carpet.tools;

import com.useleess.carpet.core.Delta;
import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Hashes;
import com.useleess.carpet.core.Patch;
import com.useleess.carpet.core.VersionInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Build-time tool (never shipped): makes the patch from Mojang's server jar and our master server jar (Kisde Carpet,
 * which carries Bullet + VasTech, with Redstone Scope built in), then proves it: applying it to Mojang's jar gives
 * the master jar file for file, and it carries no run of {@value LeakCheck#RUN} or more bytes from Mojang's files.
 *
 * <pre>PatchMaker &lt;minecraft version&gt; &lt;mojang jar (downloaded if missing)&gt; &lt;master jar&gt; &lt;out patch&gt;</pre>
 */
public final class PatchMaker {
    private PatchMaker() {}

    public static void main(String[] args) throws IOException {
        VersionInfo v = VersionInfo.load(args[0]);
        Path vanilla = Paths.get(args[1]);
        Path master = Paths.get(args[2]);
        Path out = Paths.get(args[3]);
        Files.createDirectories(vanilla.toAbsolutePath().getParent());
        Downloader.officialJar(v, vanilla.toAbsolutePath().getParent(), vanilla, Downloader.HTTP, (k, a) -> System.out.println(k + " " + Arrays.toString(a)));
        Patch patch = make(v.minecraft, vanilla, master);
        LeakCheck.Result leaks = LeakCheck.run(patch, Patch.readAll(vanilla));
        System.out.println(summary(patch) + "; " + leaks);
        if (!leaks.clean()) throw new IllegalStateException("the patch would ship Mojang bytes: " + leaks);
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (OutputStream o = Files.newOutputStream(out)) {
            patch.write(o);
        }
        verify(out, vanilla, master);
        System.out.println("patch OK: " + out + " (" + Files.size(out) + " bytes), rebuilds the master jar exactly");
    }

    public static Patch make(String minecraft, Path vanillaJar, Path masterJar) throws IOException {
        Map<String, byte[]> vanilla = Patch.readAll(vanillaJar);
        Map<String, byte[]> master = Patch.readAll(masterJar);
        byte[] dict = Patch.dictionary(vanilla);
        Delta.Index dictIndex = Delta.dictIndex(dict);
        Map<String, String> vanillaByHash = new HashMap<>();
        for (Map.Entry<String, byte[]> e : vanilla.entrySet()) vanillaByHash.putIfAbsent(Hashes.hex(Hashes.sha1(e.getValue())), e.getKey());
        List<Patch.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : master.entrySet()) {
            String name = e.getKey();
            byte[] bytes = e.getValue();
            byte[] sha1 = Hashes.sha1(bytes);
            byte[] old = vanilla.get(name);
            String same = vanillaByHash.get(Hashes.hex(sha1));
            if (name.endsWith("/") && bytes.length == 0) entries.add(new Patch.Entry(name, Patch.Kind.DIR, "", new byte[0], sha1));
            else if (old != null && Arrays.equals(old, bytes)) entries.add(new Patch.Entry(name, Patch.Kind.SAME, "", new byte[0], sha1));
            else if (same != null) entries.add(new Patch.Entry(name, Patch.Kind.FROM, same, new byte[0], sha1));
            // a changed Mojang file is ALWAYS a delta (never shipped whole)
            else if (old != null) entries.add(new Patch.Entry(name, Patch.Kind.DELTA, "", Delta.make(old, bytes, dict, dictIndex), sha1));
            else entries.add(new Patch.Entry(name, Patch.Kind.NEW, "", Delta.make(new byte[0], bytes, dict, dictIndex), sha1));
        }
        return new Patch(minecraft, Hashes.sha1Hex(vanillaJar), entries);
    }

    /** Applies the written patch to Mojang's jar and compares the result with the master jar, file for file. */
    public static void verify(Path patchFile, Path vanillaJar, Path masterJar) throws IOException {
        Patch patch;
        try (java.io.InputStream in = Files.newInputStream(patchFile)) {
            patch = Patch.read(in);
        }
        Path built = Files.createTempFile("useleess-carpet-verify", ".jar");
        try {
            patch.apply(vanillaJar, built);
            Map<String, byte[]> got = Patch.readAll(built);
            Map<String, byte[]> want = Patch.readAll(masterJar);
            if (!new ArrayList<>(got.keySet()).equals(new ArrayList<>(want.keySet()))) {
                throw new IllegalStateException("rebuilt jar has different files than the master jar");
            }
            Iterator<byte[]> a = got.values().iterator();
            for (Map.Entry<String, byte[]> e : want.entrySet()) {
                if (!Arrays.equals(a.next(), e.getValue())) throw new IllegalStateException("rebuilt file differs: " + e.getKey());
            }
        } finally {
            Files.deleteIfExists(built);
        }
    }

    public static String summary(Patch patch) {
        Map<Patch.Kind, int[]> n = new java.util.EnumMap<>(Patch.Kind.class);
        for (Patch.Entry e : patch.entries) {
            int[] c = n.computeIfAbsent(e.kind, k -> new int[2]);
            c[0]++;
            c[1] += e.data.length;
        }
        StringBuilder s = new StringBuilder("files:");
        for (Map.Entry<Patch.Kind, int[]> e : n.entrySet()) {
            s.append(' ').append(e.getKey()).append('=').append(e.getValue()[0]).append(" (").append(e.getValue()[1]).append(" B)");
        }
        return s.toString();
    }
}
