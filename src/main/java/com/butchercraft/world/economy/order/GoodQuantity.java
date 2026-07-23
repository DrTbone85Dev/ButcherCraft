package com.butchercraft.world.economy.order;

import java.math.BigDecimal;
import java.util.Objects;

public record GoodQuantity(BigDecimal value) implements Comparable<GoodQuantity> {
    private static final int MAXIMUM_PRECISION = 38;
    private static final int MAXIMUM_SCALE = 9;
    private static final GoodQuantity ZERO = new GoodQuantity(BigDecimal.ZERO);

    public GoodQuantity {
        value = normalize(value);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Good quantity must not be negative: " + value);
        }
    }

    public static GoodQuantity of(String value) {
        try {
            return new GoodQuantity(new BigDecimal(Objects.requireNonNull(value, "value")));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Good quantity is not a canonical decimal: " + value, exception);
        }
    }

    public static GoodQuantity of(long value) {
        return new GoodQuantity(BigDecimal.valueOf(value));
    }

    public static GoodQuantity zero() {
        return ZERO;
    }

    public GoodQuantity requirePositive(String label) {
        if (!isPositive()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return this;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public GoodQuantity add(GoodQuantity other) {
        return new GoodQuantity(value.add(Objects.requireNonNull(other, "other").value));
    }

    public GoodQuantity subtract(GoodQuantity other) {
        BigDecimal result = value.subtract(Objects.requireNonNull(other, "other").value);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("Good quantity subtraction would become negative");
        }
        return new GoodQuantity(result);
    }

    public String canonicalValue() {
        return value.toPlainString();
    }

    @Override
    public int compareTo(GoodQuantity other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    private static BigDecimal normalize(BigDecimal source) {
        BigDecimal normalized = Objects.requireNonNull(source, "value").stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.scale() > MAXIMUM_SCALE) {
            throw new IllegalArgumentException("Good quantity exceeds maximum scale " + MAXIMUM_SCALE + ": " + source);
        }
        if (normalized.precision() > MAXIMUM_PRECISION) {
            throw new IllegalArgumentException(
                    "Good quantity exceeds maximum precision " + MAXIMUM_PRECISION + ": " + source
            );
        }
        return normalized;
    }
}
