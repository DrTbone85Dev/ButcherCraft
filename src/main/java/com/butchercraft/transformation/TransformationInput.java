package com.butchercraft.transformation;

import java.util.Objects;

/**
 * Required material amount for a transformation.
 */
public record TransformationInput(MaterialAmount requiredAmount) {
    public TransformationInput {
        Objects.requireNonNull(requiredAmount, "requiredAmount");
    }
}
