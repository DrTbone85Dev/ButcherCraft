package com.butchercraft.transformation;

import java.util.List;
import java.util.Objects;

/**
 * Immutable material-store quantity snapshot used for atomic rollback.
 */
public record TransformationMaterialStoreSnapshot(List<MaterialAmount> materials) {
    public TransformationMaterialStoreSnapshot {
        materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
    }
}
