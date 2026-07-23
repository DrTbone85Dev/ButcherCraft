package com.butchercraft.world.allocation;

public record AllocationCycleContext(
        AllocationCycleId cycleId,
        long simulationTick,
        AllocationPolicyId policyId,
        AllocationMetadata metadata,
        int schemaVersion
) {
    public AllocationCycleContext {
        cycleId = AllocationValidation.required(cycleId, "cycleId");
        if (simulationTick < 0L) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_SIMULATION_TICK,
                    AllocationCycleFailureScope.CYCLE,
                    "simulationTick",
                    "Allocation Cycle simulation tick must not be negative"
            );
        }
        policyId = AllocationValidation.required(policyId, "policyId");
        metadata = AllocationValidation.required(metadata, "metadata");
        if (schemaVersion != AllocationSchema.CURRENT_VERSION) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_SCHEMA_VERSION,
                    AllocationCycleFailureScope.CYCLE,
                    "schemaVersion",
                    "Unsupported Allocation Cycle schema version: " + schemaVersion
            );
        }
        if (!cycleId.equals(AllocationCycleId.forTick(simulationTick))) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_CYCLE_ID,
                    AllocationCycleFailureScope.CYCLE,
                    cycleId.value(),
                    "Allocation Cycle identity must match its simulation tick"
            );
        }
        if (!AllocationPolicies.FIRST_FIT.equals(policyId)) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.UNSUPPORTED_POLICY,
                    AllocationCycleFailureScope.CYCLE,
                    policyId.value(),
                    "Allocation schema 1 supports deterministic first-fit only"
            );
        }
    }

    public static AllocationCycleContext firstFit(long simulationTick) {
        if (simulationTick < 0L) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_SIMULATION_TICK,
                    AllocationCycleFailureScope.CYCLE,
                    "simulationTick",
                    "Allocation Cycle simulation tick must not be negative"
            );
        }
        return new AllocationCycleContext(
                AllocationCycleId.forTick(simulationTick),
                simulationTick,
                AllocationPolicies.FIRST_FIT,
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
    }
}
