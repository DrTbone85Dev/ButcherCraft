package com.butchercraft.architecture.validation;

import java.util.Objects;

public record SimulationInvariantDescriptor(
        ArchitectureId id,
        SimulationInvariantType type,
        boolean satisfied,
        String description
) {
    public SimulationInvariantDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        description = Objects.requireNonNull(description, "description").strip();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }
}
