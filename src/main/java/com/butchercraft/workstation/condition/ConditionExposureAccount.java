package com.butchercraft.workstation.condition;

import java.util.Objects;

public record ConditionExposureAccount(
        ConditionExposureType type, long eligibleTicks, long graceProgress, ConditionRemainder remainder
) implements Comparable<ConditionExposureAccount> {
    public ConditionExposureAccount {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(remainder, "remainder");
        if (eligibleTicks < 0 || graceProgress < 0 || graceProgress > eligibleTicks) {
            throw new IllegalArgumentException("Invalid cumulative exposure/grace state");
        }
    }

    public static ConditionExposureAccount empty(ConditionExposureType type) {
        return new ConditionExposureAccount(type, 0, 0, ConditionRemainder.ZERO);
    }

    void digestInto(ConditionDigest digest) {
        digest.add(type.name()).add(eligibleTicks).add(graceProgress)
                .add(remainder.numerator()).add(remainder.denominator());
    }

    @Override
    public int compareTo(ConditionExposureAccount other) {
        return type.compareTo(other.type);
    }
}
