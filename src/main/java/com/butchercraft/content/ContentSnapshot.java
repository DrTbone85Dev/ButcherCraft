package com.butchercraft.content;

import com.butchercraft.product.definition.ProductRegistry;
import com.butchercraft.transformation.TransformationRegistry;

import java.util.Objects;

/**
 * Immutable active content view assembled from datapack-backed registries.
 */
public record ContentSnapshot(
        ProductRegistry products,
        TransformationRegistry transformations
) {
    public ContentSnapshot {
        Objects.requireNonNull(products, "products");
        Objects.requireNonNull(transformations, "transformations");
    }
}
