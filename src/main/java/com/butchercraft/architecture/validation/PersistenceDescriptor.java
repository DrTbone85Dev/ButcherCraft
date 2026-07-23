package com.butchercraft.architecture.validation;

import java.util.List;
import java.util.Objects;

public record PersistenceDescriptor(
        String id,
        String path,
        ArchitectureId ownerId,
        int schemaVersion,
        PersistenceDataKind dataKind,
        OrderingPolicy orderingPolicy,
        List<ArchitectureReference> references
) {
    public PersistenceDescriptor {
        id = requireText(id, "id");
        path = requireText(path, "path").replace('\\', '/');
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(dataKind, "dataKind");
        Objects.requireNonNull(orderingPolicy, "orderingPolicy");
        references = Objects.requireNonNull(references, "references").stream()
                .map(reference -> Objects.requireNonNull(reference, "reference"))
                .toList();
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
