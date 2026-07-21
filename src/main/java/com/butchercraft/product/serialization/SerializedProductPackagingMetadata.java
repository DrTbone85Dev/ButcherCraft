package com.butchercraft.product.serialization;

import java.util.Objects;

/**
 * Serialized representation of optional retail packaging metadata on product definitions.
 */
public record SerializedProductPackagingMetadata(
        String definition,
        String sourceProduct
) {
    public SerializedProductPackagingMetadata {
        definition = requiredString(definition, "definition");
        sourceProduct = requiredString(sourceProduct, "sourceProduct");
    }

    private static String requiredString(String value, String fieldName) {
        String trimmed = Objects.requireNonNull(value, fieldName).strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Serialized product packaging " + fieldName + " cannot be blank");
        }
        return trimmed;
    }
}
