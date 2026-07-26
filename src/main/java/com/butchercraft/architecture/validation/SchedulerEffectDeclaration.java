package com.butchercraft.architecture.validation;

import java.util.Locale;
import java.util.Objects;

public record SchedulerEffectDeclaration(
        ArchitectureId id,
        String effectKind,
        ArchitectureId ownerId,
        ArchitectureValidationDisposition disposition,
        String source,
        String description
) {
    public SchedulerEffectDeclaration {
        Objects.requireNonNull(id, "id");
        effectKind = requireText(effectKind, "effectKind").toUpperCase(Locale.ROOT);
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
