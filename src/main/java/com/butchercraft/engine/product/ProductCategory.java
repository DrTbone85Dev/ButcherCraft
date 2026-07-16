package com.butchercraft.engine.product;

import com.butchercraft.engine.EngineId;

import java.util.Objects;

/**
 * Coarse immutable source category for a product.
 *
 * <p>The category is an id value instead of a closed enum so datapack-defined species can pass
 * through the Minecraft-independent engine without adding species-specific engine constants.</p>
 */
public record ProductCategory(EngineId id) {
    public static final ProductCategory BEEF = fromId(EngineId.of("butchercraft:beef"));
    public static final ProductCategory GENERIC = fromId(EngineId.of("butchercraft:generic"));

    public ProductCategory {
        Objects.requireNonNull(id, "id");
    }

    public static ProductCategory fromId(EngineId id) {
        return new ProductCategory(id);
    }
}
