package com.butchercraft.architecture.validation;

import java.util.Objects;

public record OwnershipContract(
        ArchitectureId responsibilityId,
        ArchitectureId expectedOwnerId,
        ValidationCategory category,
        String rationale
) {
    public OwnershipContract {
        Objects.requireNonNull(responsibilityId, "responsibilityId");
        Objects.requireNonNull(expectedOwnerId, "expectedOwnerId");
        Objects.requireNonNull(category, "category");
        rationale = Objects.requireNonNull(rationale, "rationale").strip();
        if (rationale.isEmpty()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }
}
