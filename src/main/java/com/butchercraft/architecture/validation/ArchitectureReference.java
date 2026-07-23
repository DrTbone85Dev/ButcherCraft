package com.butchercraft.architecture.validation;

import java.util.Objects;

public record ArchitectureReference(
        String registryId,
        String entryId
) {
    public ArchitectureReference {
        registryId = requireText(registryId, "registryId");
        entryId = requireText(entryId, "entryId");
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
