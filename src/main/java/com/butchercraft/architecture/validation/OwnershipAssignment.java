package com.butchercraft.architecture.validation;

import java.util.Objects;

public record OwnershipAssignment(
        ArchitectureId responsibilityId,
        ArchitectureId ownerId
) {
    public OwnershipAssignment {
        Objects.requireNonNull(responsibilityId, "responsibilityId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
