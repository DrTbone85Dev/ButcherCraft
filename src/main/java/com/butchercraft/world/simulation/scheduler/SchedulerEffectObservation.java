package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SchedulerEffectObservation(
        int schemaVersion,
        SchedulerEffectIdentity effectIdentity,
        HandlerEffectType effectType,
        String ownerSubsystemId,
        String ownerResultIdentity,
        String resultContentDigest
) {
    public SchedulerEffectObservation {
        schemaVersion = SchedulerValidation.requireSchema(schemaVersion, "scheduler effect observation");
        effectIdentity = Objects.requireNonNull(effectIdentity, "effectIdentity");
        effectType = Objects.requireNonNull(effectType, "effectType");
        ownerSubsystemId = SchedulerValidation.requireId(ownerSubsystemId, "Effect observation owner id");
        ownerResultIdentity = SchedulerValidation.requireId(ownerResultIdentity, "Owner result identity");
        resultContentDigest = SchedulerIdentityDigest.requireDigest(
                resultContentDigest,
                "Effect observation content digest"
        );
        if (effectType == HandlerEffectType.READ_ONLY) {
            throw new IllegalArgumentException("READ_ONLY handlers cannot publish Scheduler effect observations");
        }
    }

    public static SchedulerEffectObservation of(
            SchedulerEffectIdentity effectIdentity,
            HandlerEffectType effectType,
            String ownerSubsystemId,
            String ownerResultIdentity,
            String resultContentDigest
    ) {
        return new SchedulerEffectObservation(
                SchedulerSchema.CURRENT_VERSION,
                effectIdentity,
                effectType,
                ownerSubsystemId,
                ownerResultIdentity,
                resultContentDigest
        );
    }

    public static SchedulerEffectObservation transactionResult(
            SchedulerEffectIdentity effectIdentity,
            String transactionResultDigest
    ) {
        String digest = SchedulerIdentityDigest.requireDigest(
                transactionResultDigest,
                "Transaction result evidence digest"
        );
        String idSuffix = SchedulerIdentityDigest.digestIdSuffix(
                transactionResultDigest,
                "Transaction result evidence digest"
        );
        return of(
                effectIdentity,
                HandlerEffectType.TRANSACTION_BACKED,
                "butchercraft:transactions",
                "butchercraft:transaction_result_evidence/sha256/" + idSuffix,
                digest
        );
    }

    public boolean sameContentAs(SchedulerEffectObservation other) {
        Objects.requireNonNull(other, "other");
        return effectIdentity.equals(other.effectIdentity)
                && effectType == other.effectType
                && ownerSubsystemId.equals(other.ownerSubsystemId)
                && ownerResultIdentity.equals(other.ownerResultIdentity)
                && resultContentDigest.equals(other.resultContentDigest);
    }
}
