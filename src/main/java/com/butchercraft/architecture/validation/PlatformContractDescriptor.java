package com.butchercraft.architecture.validation;

import java.util.Objects;

public record PlatformContractDescriptor(
        ArchitectureId id,
        ValidationCategory category,
        ArchitectureId ownerId,
        ArchitectureValidationDisposition disposition,
        String source,
        String description
) {
    public PlatformContractDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(disposition, "disposition");
        source = requireText(source, "source");
        description = requireText(description, "description");
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
