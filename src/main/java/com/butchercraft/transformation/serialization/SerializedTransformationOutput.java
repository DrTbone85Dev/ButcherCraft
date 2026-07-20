package com.butchercraft.transformation.serialization;

import java.util.Objects;

/**
 * Serialized transformation output entry.
 */
public record SerializedTransformationOutput(
        SerializedTransformationAmount amount,
        String classification
) {
    public SerializedTransformationOutput {
        Objects.requireNonNull(amount, "amount");
        classification = Objects.requireNonNull(classification, "classification").strip();
        if (classification.isEmpty()) {
            throw new IllegalArgumentException("Serialized transformation output classification cannot be blank");
        }
    }
}
