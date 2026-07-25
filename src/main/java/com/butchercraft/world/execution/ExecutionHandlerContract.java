package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.HandlerEffectType;

import java.util.Objects;

public record ExecutionHandlerContract(
        int schemaVersion,
        String handlerId,
        String operationType,
        HandlerEffectType schedulerEffectType,
        boolean ownerResultRequired,
        boolean retryCompatible,
        int maximumWorkUnits,
        String configurationIdentity
) {
    public ExecutionHandlerContract {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution handler contract");
        handlerId = ExecutionValidation.requireId(handlerId, "Execution handler id");
        operationType = ExecutionValidation.requireId(operationType, "Execution operation type");
        schedulerEffectType = Objects.requireNonNull(schedulerEffectType, "schedulerEffectType");
        maximumWorkUnits = ExecutionValidation.requirePositive(maximumWorkUnits, "Maximum handler work units");
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Execution handler configuration identity"
        );
        if (schedulerEffectType != HandlerEffectType.IDEMPOTENT) {
            throw new IllegalArgumentException("IM-011 generic Execution handlers must be Scheduler-IDEMPOTENT");
        }
        if (!ownerResultRequired) {
            throw new IllegalArgumentException("IM-011 generic Execution handlers must publish owner result evidence");
        }
    }

    public static ExecutionHandlerContract idempotent(
            String handlerId,
            String operationType,
            int maximumWorkUnits,
            String configurationIdentity
    ) {
        return new ExecutionHandlerContract(
                ExecutionSchema.CURRENT_VERSION,
                handlerId,
                operationType,
                HandlerEffectType.IDEMPOTENT,
                true,
                false,
                maximumWorkUnits,
                configurationIdentity
        );
    }

    public String contractIdentity() {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_handler_contract");
        digest.add(schemaVersion)
                .add(handlerId)
                .add(operationType)
                .add(schedulerEffectType.name())
                .add(ownerResultRequired)
                .add(retryCompatible)
                .add(maximumWorkUnits)
                .add(configurationIdentity);
        return "butchercraft:execution_handler_contract/v" + schemaVersion + "/"
                + ExecutionValidation.digestIdSuffix(digest.finish());
    }
}
