package com.butchercraft.architecture.validation;

import java.util.Objects;

public record DependencyDescriptor(
        ArchitectureId consumerId,
        ArchitectureId providerId
) {
    public DependencyDescriptor {
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(providerId, "providerId");
        if (consumerId.equals(providerId)) {
            throw new IllegalArgumentException("A component cannot depend upon itself");
        }
    }
}
