package com.redstonescope.carpetbuild;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Public door to Redstone Scope's {@code Mappings} (its source is compiled in from redstone-scope, never copied; see
 * CODE_MAP.md): obfuscated server names <-> SRG names of Minecraft 1.12.2. Lives in that package only because
 * {@code Mappings} is package-private.
 */
public final class NameBridge {
    private final Mappings mappings;

    public NameBridge(Path obfToSrg, Map<String, byte[]> serverJar) throws IOException {
        mappings = new Mappings(obfToSrg, serverJar);
    }

    /** A server class (obfuscated names) in SRG names. */
    public byte[] toSrg(byte[] obf) {
        return mappings.toSrg(obf);
    }

    /** A class in SRG names in the server's obfuscated names. */
    public byte[] toObf(byte[] srg) {
        return mappings.toObf(srg);
    }

    /** The obfuscated class name of a readable one (the same name when it is not obfuscated). */
    public String obfClass(String readable) {
        return mappings.obfClass(readable);
    }
}
