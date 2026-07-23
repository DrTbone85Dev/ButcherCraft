package com.butchercraft.world.allocation;

import java.math.BigDecimal;

public record AllocationQuantity(
        BigDecimal amount,
        CapacityUnitId unitId
) implements Comparable<AllocationQuantity> {
    public AllocationQuantity {
        unitId = AllocationValidation.required(unitId, "unitId");
        amount = normalize(amount);
        if (amount.signum() < 0) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.NEGATIVE_QUANTITY,
                    "amount",
                    "Allocation quantity must not be negative: " + amount.toPlainString()
            );
        }
    }

    public static AllocationQuantity of(String amount, CapacityUnitId unitId) {
        if (amount == null) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.NULL_VALUE,
                    "amount",
                    "Allocation quantity amount is required"
            );
        }
        if (amount.contains("e") || amount.contains("E")) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.NONCANONICAL_INPUT,
                    "amount",
                    "Allocation quantity does not accept scientific notation: " + amount
            );
        }
        try {
            return new AllocationQuantity(new BigDecimal(amount), unitId);
        } catch (NumberFormatException exception) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.NONCANONICAL_INPUT,
                    "amount",
                    "Allocation quantity is not an exact decimal: " + amount
            );
        }
    }

    public static AllocationQuantity of(long amount, CapacityUnitId unitId) {
        return new AllocationQuantity(BigDecimal.valueOf(amount), unitId);
    }

    public static AllocationQuantity zero(CapacityUnitId unitId) {
        return new AllocationQuantity(BigDecimal.ZERO, unitId);
    }

    public AllocationQuantity requirePositive(String field) {
        if (!isPositive()) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.ZERO_QUANTITY,
                    field,
                    field + " must be positive"
            );
        }
        return this;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isCompatibleWith(AllocationQuantity other) {
        return unitId.equals(AllocationValidation.required(other, "other").unitId);
    }

    public AllocationQuantity add(AllocationQuantity other) {
        AllocationQuantity compatible = requireCompatible(other);
        try {
            return new AllocationQuantity(amount.add(compatible.amount), unitId);
        } catch (ArithmeticException exception) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.ARITHMETIC_OVERFLOW,
                    "amount",
                    "Allocation quantity addition exceeded schema bounds"
            );
        }
    }

    public AllocationQuantity subtract(AllocationQuantity other) {
        AllocationQuantity compatible = requireCompatible(other);
        BigDecimal result = amount.subtract(compatible.amount);
        if (result.signum() < 0) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.QUANTITY_UNDERFLOW,
                    "amount",
                    "Allocation quantity subtraction would become negative"
            );
        }
        return new AllocationQuantity(result, unitId);
    }

    public int compareAmount(AllocationQuantity other) {
        return amount.compareTo(requireCompatible(other).amount);
    }

    public String canonicalAmount() {
        return amount.toPlainString();
    }

    public String canonicalValue() {
        return canonicalAmount() + " " + unitId.value();
    }

    @Override
    public int compareTo(AllocationQuantity other) {
        int unit = unitId.compareTo(AllocationValidation.required(other, "other").unitId);
        return unit != 0 ? unit : amount.compareTo(other.amount);
    }

    private AllocationQuantity requireCompatible(AllocationQuantity other) {
        AllocationQuantity candidate = AllocationValidation.required(other, "other");
        if (!unitId.equals(candidate.unitId)) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.INCOMPATIBLE_UNIT,
                    "unitId",
                    "Allocation quantity units are incompatible: "
                            + unitId.value() + " and " + candidate.unitId.value()
            );
        }
        return candidate;
    }

    private static BigDecimal normalize(BigDecimal source) {
        if (source == null) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.NULL_VALUE,
                    "amount",
                    "Allocation quantity amount is required"
            );
        }
        BigDecimal normalized = source.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.scale() > AllocationSchema.MAXIMUM_DECIMAL_SCALE) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.ARITHMETIC_OVERFLOW,
                    "amount",
                    "Allocation quantity exceeds maximum scale "
                            + AllocationSchema.MAXIMUM_DECIMAL_SCALE
            );
        }
        if (normalized.precision() > AllocationSchema.MAXIMUM_DECIMAL_PRECISION) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.ARITHMETIC_OVERFLOW,
                    "amount",
                    "Allocation quantity exceeds maximum precision "
                            + AllocationSchema.MAXIMUM_DECIMAL_PRECISION
            );
        }
        return normalized;
    }
}
