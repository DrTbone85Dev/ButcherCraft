package com.butchercraft.architecture.validation;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ArchitectureId(String value) implements Comparable<ArchitectureId> {
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-z][a-z0-9_]*(?::[a-z][a-z0-9_]*(?:/[a-z][a-z0-9_]*)*)?"
    );

    public ArchitectureId {
        value = Objects.requireNonNull(value, "value").strip().toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid architecture id: " + value);
        }
    }

    public static ArchitectureId of(String value) {
        return new ArchitectureId(value);
    }

    @Override
    public int compareTo(ArchitectureId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
