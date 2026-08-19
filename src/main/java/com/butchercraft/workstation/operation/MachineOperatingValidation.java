package com.butchercraft.workstation.operation;

import java.util.Objects;
import java.util.regex.Pattern;

final class MachineOperatingValidation {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private MachineOperatingValidation() {
    }

    static String id(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!ID.matcher(value).matches()) throw new IllegalArgumentException(label + " must be canonical: " + value);
        return value;
    }

    static String digest(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    static String text(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
