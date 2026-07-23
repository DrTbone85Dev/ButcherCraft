package com.butchercraft.architecture.validation;

import java.util.Objects;

public record ArchitectureComponent(
        ArchitectureId id,
        String displayName,
        String packageRoot
) {
    public ArchitectureComponent {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        packageRoot = requireText(packageRoot, "packageRoot");
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
