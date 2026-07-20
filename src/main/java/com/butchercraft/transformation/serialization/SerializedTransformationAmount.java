package com.butchercraft.transformation.serialization;

import java.util.Objects;

/**
 * Serialized product quantity reference used by transformation inputs and outputs.
 */
public record SerializedTransformationAmount(
        String productId,
        long quantity,
        String unit
) {
    public SerializedTransformationAmount {
        productId = Objects.requireNonNull(productId, "productId").strip();
        unit = Objects.requireNonNull(unit, "unit").strip();
        if (productId.isEmpty()) {
            throw new IllegalArgumentException("Serialized transformation product id cannot be blank");
        }
        if (unit.isEmpty()) {
            throw new IllegalArgumentException("Serialized transformation unit cannot be blank");
        }
    }
}
