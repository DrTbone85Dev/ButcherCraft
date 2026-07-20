package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.Objects;

/**
 * Exact positive quantity of one material or product definition.
 */
public record MaterialAmount(EngineId materialId, ProductQuantity quantity) {
    public static final long MAX_SAFE_AMOUNT = Long.MAX_VALUE / 2;

    public MaterialAmount {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.amount() <= 0) {
            throw new IllegalArgumentException("Material amount must be positive");
        }
        if (quantity.amount() > MAX_SAFE_AMOUNT) {
            throw new IllegalArgumentException("Material amount is too large for safe exact transformation arithmetic");
        }
    }

    public static MaterialAmount grams(String materialId, long amount) {
        return new MaterialAmount(EngineId.of(materialId), ProductQuantity.grams(amount));
    }
}
