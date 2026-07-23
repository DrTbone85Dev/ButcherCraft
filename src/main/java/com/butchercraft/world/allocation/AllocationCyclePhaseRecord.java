package com.butchercraft.world.allocation;

public record AllocationCyclePhaseRecord(
        AllocationCyclePhase phase,
        int position,
        long operationCount,
        String stateDigest
) implements Comparable<AllocationCyclePhaseRecord> {
    public AllocationCyclePhaseRecord {
        phase = AllocationValidation.required(phase, "phase");
        if (position < 1 || position > AllocationSchema.MAXIMUM_TRACE_PHASES) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.CYCLE,
                    phase.name(),
                    "Trace phase position is outside the schema bound"
            );
        }
        if (operationCount < 0L) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.CYCLE,
                    phase.name(),
                    "Trace operation count must not be negative"
            );
        }
        stateDigest = AllocationCanonicalDigest.validate(
                stateDigest,
                "stateDigest"
        );
    }

    @Override
    public int compareTo(AllocationCyclePhaseRecord other) {
        int order = Integer.compare(
                position,
                AllocationValidation.required(other, "other").position
        );
        return order != 0 ? order : phase.compareTo(other.phase);
    }
}
