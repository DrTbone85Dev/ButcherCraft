package com.butchercraft.workstation.endpoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class WorkstationEndpointCanonicalDigest {
    private final MessageDigest digest;

    private WorkstationEndpointCanonicalDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
        add(domain);
    }

    static WorkstationEndpointCanonicalDigest create(String domain) {
        return new WorkstationEndpointCanonicalDigest(domain);
    }

    WorkstationEndpointCanonicalDigest add(String value) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) bytes.length);
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 24));
        digest.update(bytes);
        return this;
    }

    WorkstationEndpointCanonicalDigest add(long value) {
        return add(Long.toString(value));
    }

    WorkstationEndpointCanonicalDigest add(int value) {
        return add(Integer.toString(value));
    }

    WorkstationEndpointCanonicalDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    WorkstationEndpointCanonicalDigest add(byte[] value) {
        byte[] bytes = Objects.requireNonNull(value, "value");
        add(bytes.length);
        digest.update(bytes);
        return this;
    }

    String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    static String suffix(String digest) {
        return WorkstationEndpointValidation.digest(digest, "digest").substring("sha256:".length());
    }
}
