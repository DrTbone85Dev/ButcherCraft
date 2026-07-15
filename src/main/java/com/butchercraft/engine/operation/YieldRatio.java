package com.butchercraft.engine.operation;

import com.butchercraft.engine.quantity.ProductQuantity;

import java.math.BigInteger;

/**
 * Immutable exact yield ratio for processing output.
 *
 * <p>The ratio applies with integer arithmetic. Yield modifiers are additive basis points where
 * 10,000 basis points equal 100%. Results round half up to the nearest smallest unit. Minecraft
 * inventory behavior is handled later at the integration boundary.</p>
 */
public record YieldRatio(long numerator, long denominator) {
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10_000L);

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
     * @throws ArithmeticException when multiplication or the final quantity overflows {@code long}
     */
    public ProductQuantity apply(ProductQuantity quantity) {
        return apply(quantity, 0);
    }

    /**
     * Applies this yield ratio plus an additive basis-point modifier.
     *
     * @param quantity non-null input quantity
     * @param yieldBasisPointsDelta additive yield adjustment; 100 basis points equals 1%
     * @return rounded half-up output quantity in the same unit
     * @throws IllegalArgumentException when the effective yield would be negative
     * @throws ArithmeticException when the final quantity does not fit in {@code long}
     */
    public ProductQuantity apply(ProductQuantity quantity, int yieldBasisPointsDelta) {
        BigInteger baseNumerator = BigInteger.valueOf(numerator).multiply(BASIS_POINTS);
        BigInteger modifierNumerator = BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(yieldBasisPointsDelta));
        BigInteger effectiveNumerator = baseNumerator.add(modifierNumerator);
        if (effectiveNumerator.signum() < 0) {
            throw new IllegalArgumentException("Effective yield cannot be negative");
        }

        BigInteger effectiveDenominator = BigInteger.valueOf(denominator).multiply(BASIS_POINTS);
        BigInteger raw = BigInteger.valueOf(quantity.amount()).multiply(effectiveNumerator);
        BigInteger[] divided = raw.divideAndRemainder(effectiveDenominator);
        BigInteger rounded = divided[0];
        if (divided[1].multiply(BigInteger.TWO).compareTo(effectiveDenominator) >= 0) {
            rounded = rounded.add(BigInteger.ONE);
        }
        if (rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("Yield result exceeds supported quantity range");
        }
        return new ProductQuantity(rounded.longValueExact(), quantity.unit());
    }
}
