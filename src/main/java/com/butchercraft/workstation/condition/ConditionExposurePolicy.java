package com.butchercraft.workstation.condition;

import java.util.Objects;

public record ConditionExposurePolicy(
        ConditionExposureType type,
        long graceTicks,
        long lossNumerator,
        long lossDenominator,
        long settlementTicks,
        boolean resetGraceOnSuccessfulProcessing
) implements Comparable<ConditionExposurePolicy> {
    public ConditionExposurePolicy {
        type = Objects.requireNonNull(type, "type");
        if (graceTicks < 0 || lossNumerator < 0 || lossDenominator <= 0 || settlementTicks <= 0) {
            throw new IllegalArgumentException("Invalid exact exposure policy bounds");
        }
    }

    public boolean consequential() {
        return lossNumerator != 0;
    }

    void digestInto(ConditionDigest digest) {
        digest.add(type.name()).add(graceTicks).add(lossNumerator).add(lossDenominator)
                .add(settlementTicks).add(resetGraceOnSuccessfulProcessing);
    }

    @Override
    public int compareTo(ConditionExposurePolicy other) {
        return type.compareTo(other.type);
    }
}
