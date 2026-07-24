package com.butchercraft.architecture.validation;

import java.util.Objects;

public record ArchitectureDocumentDescriptor(
        ArchitectureId id,
        String path,
        String status,
        String revision,
        ArchitectureValidationDisposition disposition
) {
    public ArchitectureDocumentDescriptor {
        Objects.requireNonNull(id, "id");
        path = requireText(path, "path").replace('\\', '/');
        status = requireText(status, "status");
        revision = requireText(revision, "revision");
        Objects.requireNonNull(disposition, "disposition");
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
