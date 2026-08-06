package com.butchercraft.world.materialhandling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class MaterialHandlingDigest {
    private final MessageDigest digest;

    private MaterialHandlingDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
        add(domain);
    }

    static MaterialHandlingDigest create(String domain) {
        return new MaterialHandlingDigest(domain);
    }

    MaterialHandlingDigest add(String value) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) bytes.length);
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 24));
        digest.update(bytes);
        return this;
    }

    MaterialHandlingDigest add(long value) {
        return add(Long.toString(value));
    }

    MaterialHandlingDigest add(int value) {
        return add(Integer.toString(value));
    }

    String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    static String suffix(String digest) {
        return MaterialHandlingValidation.digest(digest, "digest").substring("sha256:".length());
    }
}
