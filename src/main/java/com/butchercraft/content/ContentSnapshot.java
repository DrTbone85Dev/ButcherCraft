package com.butchercraft.content;

import com.butchercraft.packaging.definition.PackagingRegistry;
import com.butchercraft.product.definition.ProductRegistry;
import com.butchercraft.transformation.TransformationRegistry;

import java.util.Objects;

/**
 * Immutable active content view assembled from datapack-backed registries.
 */
public record ContentSnapshot(
        ProductRegistry products,
        PackagingRegistry packaging,
        TransformationRegistry transformations
) {
    public ContentSnapshot {
        Objects.requireNonNull(products, "products");
        Objects.requireNonNull(packaging, "packaging");
        Objects.requireNonNull(transformations, "transformations");
    }
}
