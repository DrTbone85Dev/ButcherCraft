package com.butchercraft.world.workforce.machineoperation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class EmployeeMachineOperationDigest {
    private EmployeeMachineOperationDigest() {
    }

    static String sha256(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String requireIdentity(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(label + " must be a canonical identity");
        }
        return normalized;
    }
}
