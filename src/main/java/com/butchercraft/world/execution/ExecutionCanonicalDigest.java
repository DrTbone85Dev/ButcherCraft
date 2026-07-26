package com.butchercraft.world.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ExecutionCanonicalDigest {
    private final MessageDigest digest;

    private ExecutionCanonicalDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
        add(domain);
    }

    static ExecutionCanonicalDigest create(String domain) {
        return new ExecutionCanonicalDigest(ExecutionValidation.requireId(domain, "Digest domain"));
    }

    ExecutionCanonicalDigest add(String value) {
        String normalized = value == null ? "<null>" : value;
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) bytes.length);
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 24));
        digest.update(bytes);
        return this;
    }

    ExecutionCanonicalDigest add(long value) {
        return add(Long.toString(value));
    }

    ExecutionCanonicalDigest add(int value) {
        return add(Integer.toString(value));
    }

    ExecutionCanonicalDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }
}
