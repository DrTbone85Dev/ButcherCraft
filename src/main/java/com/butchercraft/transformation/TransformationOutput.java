package com.butchercraft.transformation;

import java.util.Objects;

/**
 * Produced material amount for a transformation.
 */
public record TransformationOutput(
        MaterialAmount producedAmount,
        TransformationOutputClassification classification
) {
    public TransformationOutput {
        Objects.requireNonNull(producedAmount, "producedAmount");
        Objects.requireNonNull(classification, "classification");
    }
}
