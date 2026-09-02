package com.butchercraft.workstation.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class WorkstationReservationCanonicalDigest {
    private final MessageDigest digest;

    private WorkstationReservationCanonicalDigest(String domain) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
        add(domain);
    }

    static WorkstationReservationCanonicalDigest create(String domain) {
        return new WorkstationReservationCanonicalDigest(domain);
    }

    WorkstationReservationCanonicalDigest add(String value) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 0);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
        return this;
    }

    WorkstationReservationCanonicalDigest add(long value) {
        return add(Long.toString(value));
    }

    WorkstationReservationCanonicalDigest add(int value) {
        return add(Integer.toString(value));
    }

    WorkstationReservationCanonicalDigest add(boolean value) {
        return add(Boolean.toString(value));
    }

    String finish() {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    static String suffix(String digest) {
        String prefix = "sha256:";
        if (!Objects.requireNonNull(digest, "digest").startsWith(prefix)) {
            throw new IllegalArgumentException("Reservation digest must use SHA-256");
        }
        return digest.substring(prefix.length());
    }
}
