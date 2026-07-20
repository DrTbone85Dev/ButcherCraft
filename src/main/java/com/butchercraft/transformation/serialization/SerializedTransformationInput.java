package com.butchercraft.transformation.serialization;

import java.util.Objects;

/**
 * Serialized transformation input entry.
 */
public record SerializedTransformationInput(
        SerializedTransformationAmount amount
) {
    public SerializedTransformationInput {
        Objects.requireNonNull(amount, "amount");
    }
}
