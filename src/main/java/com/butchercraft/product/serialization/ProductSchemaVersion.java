package com.butchercraft.product.serialization;

import com.butchercraft.product.definition.ProductDefinition;

/**
 * Stable product-definition schema version used by serialized content.
 */
public record ProductSchemaVersion(int value) {
    public static final ProductSchemaVersion CURRENT = new ProductSchemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION);

    public ProductSchemaVersion {
        if (value <= 0) {
            throw new IllegalArgumentException("Product schema version must be positive");
        }
    }
}
