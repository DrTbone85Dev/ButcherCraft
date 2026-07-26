package com.butchercraft.world.checkpoint;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class CheckpointSnapshotDigest {
    private CheckpointSnapshotDigest() {
    }

    public static String sha256(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(bytes.clone()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String shortHex(String digest) {
        String value = CheckpointValidation.digest(digest, "digest");
        return value.substring("sha256:".length(), "sha256:".length() + 16);
    }
}
