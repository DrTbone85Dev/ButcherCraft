package com.butchercraft.architecture.validation;

import java.util.Objects;

public record DependencyConstraint(
        ArchitectureId consumerId,
        ArchitectureId forbiddenProviderId,
        String rationale
) {
    public DependencyConstraint {
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(forbiddenProviderId, "forbiddenProviderId");
        rationale = Objects.requireNonNull(rationale, "rationale").strip();
        if (rationale.isEmpty()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }
}
