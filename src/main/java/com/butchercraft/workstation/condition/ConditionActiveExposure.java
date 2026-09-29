package com.butchercraft.workstation.condition;

import java.util.Objects;

public record ConditionActiveExposure(
        String identity, ConditionExposureType type, String policyIdentity,
        String operatingTransitionIdentity, long operatingRevision, String availabilityProofIdentity,
        long startTick, long accountedThroughTick, long openingConditionRevision
) {
    public ConditionActiveExposure {
        ConditionDigest.text(identity, "identity");
        Objects.requireNonNull(type, "type");
        ConditionDigest.text(policyIdentity, "policyIdentity");
        ConditionDigest.text(operatingTransitionIdentity, "operatingTransitionIdentity");
        ConditionDigest.text(availabilityProofIdentity, "availabilityProofIdentity");
        if (operatingRevision <= 0 || startTick < 0 || accountedThroughTick < startTick
                || openingConditionRevision <= 0) throw new IllegalArgumentException("Invalid active exposure");
    }

    public ConditionActiveExposure accountedThrough(long tick) {
        if (tick < accountedThroughTick) throw new IllegalArgumentException("Exposure tick regression");
        return new ConditionActiveExposure(identity, type, policyIdentity, operatingTransitionIdentity,
                operatingRevision, availabilityProofIdentity, startTick, tick, openingConditionRevision);
    }

    void digestInto(ConditionDigest digest) {
        digest.add(identity).add(type.name()).add(policyIdentity).add(operatingTransitionIdentity)
                .add(operatingRevision).add(availabilityProofIdentity).add(startTick).add(accountedThroughTick)
                .add(openingConditionRevision);
    }
}
