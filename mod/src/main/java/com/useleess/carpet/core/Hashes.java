package com.useleess.carpet.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-1 fingerprints (the hash Mojang publishes for its server jars). */
public final class Hashes {
    private Hashes() {}

    public static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sha1(byte[] data) {
        return sha1().digest(data);
    }

    public static String sha1Hex(Path file) throws IOException {
        MessageDigest md = sha1();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    public static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return s.toString();
    }
}
