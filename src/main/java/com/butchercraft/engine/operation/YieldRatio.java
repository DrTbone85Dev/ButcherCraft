package com.butchercraft.engine.operation;

import com.butchercraft.engine.quantity.ProductQuantity;

/**
 * Immutable exact yield ratio for processing output.
 *
 * <p>The ratio applies with integer arithmetic and rejects non-exact output quantities rather
 * than silently losing product. Minecraft inventory rounding can be handled later at the
 * integration boundary.</p>
 */
public record YieldRatio(long numerator, long denominator) {
    public YieldRatio {
        if (numerator < 0) {
            throw new IllegalArgumentException("Yield numerator cannot be negative");
        }
        if (denominator <= 0) {
            throw new IllegalArgumentException("Yield denominator must be positive");
        }
    }

    public static YieldRatio identity() {
        return new YieldRatio(1, 1);
    }

    /**
     * Applies this yield ratio to a quantity with exact arithmetic.
     *
     * @param quantity non-null input quantity
     * @return exact output quantity in the same unit
     * @throws ArithmeticException when multiplication overflows
     * @throws IllegalArgumentException when the ratio would require rounding
     */
    public ProductQuantity apply(ProductQuantity quantity) {
        long multiplied = Math.multiplyExact(quantity.amount(), numerator);
        if (multiplied % denominator != 0) {
            throw new IllegalArgumentException("Yield ratio must produce an exact quantity in this milestone");
        }
        return new ProductQuantity(multiplied / denominator, quantity.unit());
    }
}
