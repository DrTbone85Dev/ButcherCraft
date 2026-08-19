package com.butchercraft.workstation.operation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class MachineOperatingDigest {
    private final MessageDigest digest;

    private MachineOperatingDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
        add(domain);
    }

    static MachineOperatingDigest create(String domain) {
        return new MachineOperatingDigest(domain);
    }

    MachineOperatingDigest add(String value) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) bytes.length);
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 24));
        digest.update(bytes);
        return this;
    }

    MachineOperatingDigest add(long value) {
        return add(Long.toString(value));
    }

    MachineOperatingDigest add(int value) {
        return add(Integer.toString(value));
    }

    MachineOperatingDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    static String suffix(String digest) {
        return MachineOperatingValidation.digest(digest, "digest").substring("sha256:".length());
    }
}
