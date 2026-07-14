package com.butchercraft.engine.product;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.Objects;

/**
 * Immutable domain representation of a product.
 *
 * <p>A product has a stable engine id, source category, processing state, exact quantity, and
 * quality value. Equality is value equality across all fields. The type deliberately has no
 * Minecraft or NeoForge dependency; item stacks and registries should convert to and from this
 * record at integration boundaries.</p>
 */
public record Product(
        EngineId typeId,
        ProductCategory sourceCategory,
        ProcessingState processingState,
        ProductQuantity quantity,
        ProductQuality quality
) {
    public Product {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(sourceCategory, "sourceCategory");
        Objects.requireNonNull(processingState, "processingState");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(quality, "quality");
    }

    /**
     * Returns a new product snapshot with the supplied quantity.
     *
     * @param newQuantity non-null replacement quantity
     * @return new immutable product; this product is unchanged
     */
    public Product withQuantity(ProductQuantity newQuantity) {
        return new Product(typeId, sourceCategory, processingState, newQuantity, quality);
    }

    /**
     * Returns a new product snapshot with the supplied quality.
     *
     * @param newQuality non-null replacement quality
     * @return new immutable product; this product is unchanged
     */
    public Product withQuality(ProductQuality newQuality) {
        return new Product(typeId, sourceCategory, processingState, quantity, newQuality);
    }
}
