package com.butchercraft.world.allocation;

public record AllocationCycleSummary(
        int observedResourceCount,
        int observedCapacityCount,
        int activeCommitmentCount,
        int evaluatedSetCount,
        int successfulSetCount,
        int waitingSetCount,
        int failedSetCount,
        int createdCommitmentCount,
        int conflictCount,
        long deterministicOperationCount,
        int schemaVersion
) {
    public AllocationCycleSummary {
        if (observedResourceCount < 0
                || observedCapacityCount < 0
                || activeCommitmentCount < 0
                || evaluatedSetCount < 0
                || successfulSetCount < 0
                || waitingSetCount < 0
                || failedSetCount < 0
                || createdCommitmentCount < 0
                || conflictCount < 0
                || deterministicOperationCount < 0L
                || evaluatedSetCount != successfulSetCount
                + waitingSetCount + failedSetCount) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.CYCLE,
                    "summary",
                    "Allocation Cycle summary counts are inconsistent"
            );
        }
        schemaVersion = AllocationValidation.schema(schemaVersion);
    }
}
