package com.butchercraft.architecture.validation;

import java.util.List;
import java.util.Objects;

public record RegistryEntryDescriptor(
        String id,
        int explicitOrder,
        List<ArchitectureReference> references
) {
    public RegistryEntryDescriptor {
        id = Objects.requireNonNull(id, "id").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        references = Objects.requireNonNull(references, "references").stream()
                .map(reference -> Objects.requireNonNull(reference, "reference"))
                .toList();
    }

    public static RegistryEntryDescriptor of(String id) {
        return new RegistryEntryDescriptor(id, 0, List.of());
    }
}
