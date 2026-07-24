package com.butchercraft.architecture.validation;

import java.util.Objects;

public record PlatformIdentityDescriptor(
        ArchitectureId id,
        PlatformIdentityKind kind,
        ArchitectureValidationDisposition disposition,
        String source,
        String description
) {
    public PlatformIdentityDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
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
