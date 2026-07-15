package com.butchercraft.engine.product;

import com.butchercraft.engine.EngineId;

import java.util.Arrays;

/**
 * Coarse immutable source category for a product.
 *
 * <p>The categories are intentionally broad and Minecraft-independent. Future integration may
 * map product definitions, tags, or datapack data onto these categories without changing the
 * engine model.</p>
 */
public enum ProductCategory {
    BEEF("butchercraft:beef"),
    PORK("butchercraft:pork"),
    POULTRY("butchercraft:poultry"),
    LAMB("butchercraft:lamb"),
    GENERIC("butchercraft:generic");

    private final EngineId id;

    ProductCategory(String id) {
        this.id = EngineId.of(id);
    }

    public EngineId id() {
        return id;
    }

    public static ProductCategory fromId(EngineId id) {
        return Arrays.stream(values())
                .filter(category -> category.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported product category id: " + id));
    }
}
