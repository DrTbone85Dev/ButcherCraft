package com.butchercraft.workstation.condition;

import java.math.BigInteger;

/** A reduced proper fraction. Overflow rejects before an owner candidate is published. */
public record ConditionRemainder(long numerator, long denominator) {
    public static final ConditionRemainder ZERO = new ConditionRemainder(0, 1);

    public ConditionRemainder {
        if (numerator < 0 || denominator <= 0 || numerator >= denominator
                || !BigInteger.valueOf(numerator).gcd(BigInteger.valueOf(denominator)).equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Condition remainder must be a reduced proper fraction");
        }
    }

    public Accumulation accumulate(long ticks, long rateNumerator, long rateDenominator) {
        if (ticks < 0 || rateNumerator < 0 || rateDenominator <= 0) {
            throw new IllegalArgumentException("Invalid condition accumulation");
        }
        BigInteger denominatorValue = BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(rateDenominator));
        BigInteger total = BigInteger.valueOf(ticks).multiply(BigInteger.valueOf(rateNumerator))
                .multiply(BigInteger.valueOf(denominator))
                .add(BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(rateDenominator)));
        BigInteger[] parts = total.divideAndRemainder(denominatorValue);
        BigInteger divisor = parts[1].gcd(denominatorValue);
        return new Accumulation(parts[0].longValueExact(), new ConditionRemainder(
                parts[1].divide(divisor).longValueExact(), denominatorValue.divide(divisor).longValueExact()));
    }

    public record Accumulation(long whole, ConditionRemainder remainder) { }
}
