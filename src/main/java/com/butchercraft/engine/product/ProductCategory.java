package com.butchercraft.engine.product;

/**
 * Coarse immutable source category for a product.
 *
 * <p>The categories are intentionally broad and Minecraft-independent. Future integration may
 * map product definitions, tags, or datapack data onto these categories without changing the
 * engine model.</p>
 */
public enum ProductCategory {
    BEEF,
    PORK,
    POULTRY,
    LAMB,
    GENERIC
}
