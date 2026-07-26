package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SchedulerEffectPolicy(
        int schemaVersion,
        SimulationWorkTypeId workTypeId,
        HandlerEffectType effectType,
        String ownerSubsystemId,
        boolean retryAllowed,
        boolean deferralAllowed,
        boolean generatedWorkAllowed,
        boolean requiresEffectIdentity,
        boolean completionRequiresOwnerResult,
        boolean retryRequiresOwnerResult,
        boolean generatedWorkRequiresOwnerResult,
        boolean exceptionCreatesUnknownOutcome,
        boolean missingCompletionEvidenceCreatesUnknownOutcome,
        String source
) {
    public SchedulerEffectPolicy {
        schemaVersion = SchedulerValidation.requireSchema(schemaVersion, "scheduler effect policy");
        workTypeId = Objects.requireNonNull(workTypeId, "workTypeId");
        effectType = Objects.requireNonNull(effectType, "effectType");
        ownerSubsystemId = SchedulerValidation.requireId(ownerSubsystemId, "Effect owner subsystem id");
        source = SchedulerValidation.requireText(source, "Effect policy source", 512);
        if (effectType == HandlerEffectType.READ_ONLY && requiresEffectIdentity) {
            throw new IllegalArgumentException("READ_ONLY policy must not require Effect Identity");
        }
        if (effectType != HandlerEffectType.READ_ONLY && !requiresEffectIdentity) {
            throw new IllegalArgumentException("Consequential Scheduler policy requires Effect Identity");
        }
        if (completionRequiresOwnerResult && !requiresEffectIdentity) {
            throw new IllegalArgumentException("Owner result evidence requires Effect Identity");
        }
    }

    public static SchedulerEffectPolicy defaultFor(SimulationWorkTypeId workTypeId, HandlerEffectType effectType) {
        Objects.requireNonNull(workTypeId, "workTypeId");
        if (Objects.requireNonNull(effectType, "effectType") != HandlerEffectType.READ_ONLY) {
            throw new IllegalArgumentException("Consequential Scheduler handlers require an explicit effect policy");
        }
        return new SchedulerEffectPolicy(
                    SchedulerSchema.CURRENT_VERSION, workTypeId, effectType,
                    "butchercraft:simulation_scheduler",
                    true, true, true,
                    false, false, false, false,
                    false, false,
                    "IM-009 default READ_ONLY Scheduler effect policy"
        );
    }

    public static SchedulerEffectPolicy idempotent(
            SimulationWorkTypeId workTypeId,
            String ownerSubsystemId,
            String source
    ) {
        return new SchedulerEffectPolicy(
                SchedulerSchema.CURRENT_VERSION,
                workTypeId,
                HandlerEffectType.IDEMPOTENT,
                ownerSubsystemId,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                source
        );
    }

    public static SchedulerEffectPolicy transactionBacked(SimulationWorkTypeId workTypeId, String source) {
        return new SchedulerEffectPolicy(
                SchedulerSchema.CURRENT_VERSION,
                workTypeId,
                HandlerEffectType.TRANSACTION_BACKED,
                "butchercraft:transactions",
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                source
        );
    }

    public static SchedulerEffectPolicy nonRepeatable(
            SimulationWorkTypeId workTypeId,
            String ownerSubsystemId,
            String source
    ) {
        return new SchedulerEffectPolicy(
                SchedulerSchema.CURRENT_VERSION,
                workTypeId,
                HandlerEffectType.NON_REPEATABLE,
                ownerSubsystemId,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                true,
                true,
                source
        );
    }

    public static SchedulerEffectPolicy nonRepeatableContinuation(
            SimulationWorkTypeId workTypeId,
            String ownerSubsystemId,
            String source
    ) {
        return new SchedulerEffectPolicy(
                SchedulerSchema.CURRENT_VERSION,
                workTypeId,
                HandlerEffectType.NON_REPEATABLE,
                ownerSubsystemId,
                false,
                true,
                false,
                true,
                false,
                false,
                false,
                true,
                true,
                source
        );
    }

    public String policyIdentity() {
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create("butchercraft:scheduler_effect_policy");
        digest.add(schemaVersion)
                .add(workTypeId.value())
                .add(effectType.name())
                .add(ownerSubsystemId)
                .add(retryAllowed)
                .add(deferralAllowed)
                .add(generatedWorkAllowed)
                .add(requiresEffectIdentity)
                .add(completionRequiresOwnerResult)
                .add(retryRequiresOwnerResult)
                .add(generatedWorkRequiresOwnerResult)
                .add(exceptionCreatesUnknownOutcome)
                .add(missingCompletionEvidenceCreatesUnknownOutcome)
                .add(source);
        return "butchercraft:scheduler_effect_policy/v" + schemaVersion + "/" + digest.finish();
    }
}
