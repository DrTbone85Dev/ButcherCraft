package com.butchercraft.engine.quantity;

import java.util.Objects;

/**
 * Immutable exact quantity value object.
 *
 * <p>Milestone 1B stores weight as whole grams in a {@code long}. Zero is allowed as a deliberate
 * quantity for validation and edge cases; negative values are rejected. Arithmetic is unit-safe,
 * overflow-checked, and never performs silent conversion. Minecraft item counts and stack limits
 * are handled outside this domain type.</p>
 */
public record ProductQuantity(long amount, QuantityUnit unit) {
    public ProductQuantity {
        if (amount < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative: " + amount);
        }
        Objects.requireNonNull(unit, "unit");
    }

    public static ProductQuantity grams(long amount) {
        return new ProductQuantity(amount, QuantityUnit.GRAM);
    }

    public boolean isZero() {
        return amount == 0;
    }

    /**
     * Adds two compatible quantities with overflow protection.
     *
     * @param other quantity with the same unit
     * @return exact sum
     * @throws IllegalArgumentException when units differ
     * @throws ArithmeticException when the sum overflows {@code long}
     */
    public ProductQuantity add(ProductQuantity other) {
        requireCompatible(other);
        return new ProductQuantity(Math.addExact(amount, other.amount), unit);
    }

    /**
     * Subtracts a compatible quantity without allowing underflow.
     *
     * @param other quantity with the same unit
     * @return exact difference
     * @throws IllegalArgumentException when units differ or the result would be negative
     */
    public ProductQuantity subtract(ProductQuantity other) {
        requireCompatible(other);
        if (other.amount > amount) {
            throw new IllegalArgumentException("Quantity subtraction would become negative");
        }
        return new ProductQuantity(amount - other.amount, unit);
    }

    private void requireCompatible(ProductQuantity other) {
        Objects.requireNonNull(other, "other");
        if (unit != other.unit) {
            throw new IllegalArgumentException("Cannot combine quantities with different units");
        }
    }
}
