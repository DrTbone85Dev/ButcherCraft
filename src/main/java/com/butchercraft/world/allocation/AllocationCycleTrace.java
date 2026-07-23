package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.List;

public record AllocationCycleTrace(
        AllocationCycleId cycleId,
        long simulationTick,
        List<AllocationCyclePhaseRecord> phases,
        String traceDigest,
        int schemaVersion
) implements Comparable<AllocationCycleTrace> {
    public AllocationCycleTrace {
        cycleId = AllocationValidation.required(cycleId, "cycleId");
        simulationTick = AllocationValidation.tick(
                simulationTick,
                "simulationTick"
        );
        List<AllocationCyclePhaseRecord> canonical = new ArrayList<>(
                AllocationValidation.required(phases, "phases")
        );
        canonical.forEach(value ->
                AllocationValidation.required(value, "phase"));
        canonical.sort(AllocationCyclePhaseRecord::compareTo);
        if (canonical.size() != AllocationSchema.MAXIMUM_TRACE_PHASES) {
            throw invalid(cycleId, "Engineering trace requires every cycle phase");
        }
        for (int index = 0; index < canonical.size(); index++) {
            AllocationCyclePhaseRecord record = canonical.get(index);
            if (record.position() != index + 1
                    || record.phase().ordinal() != index) {
                throw invalid(cycleId, "Engineering trace phase order is invalid");
            }
        }
        phases = List.copyOf(canonical);
        traceDigest = AllocationCanonicalDigest.validate(
                traceDigest,
                "traceDigest"
        );
        schemaVersion = AllocationValidation.schema(schemaVersion);
        if (!cycleId.equals(AllocationCycleId.forTick(simulationTick))) {
            throw invalid(cycleId, "Engineering trace Cycle does not match tick");
        }
        String expected = calculateDigest(cycleId, simulationTick, phases);
        if (!traceDigest.equals(expected)) {
            throw invalid(cycleId, "Engineering trace digest is invalid");
        }
    }

    public static AllocationCycleTrace create(
            AllocationCycleId cycleId,
            long simulationTick,
            List<AllocationCyclePhaseRecord> phases
    ) {
        return new AllocationCycleTrace(
                cycleId,
                simulationTick,
                phases,
                calculateDigest(cycleId, simulationTick, phases),
                AllocationSchema.CURRENT_VERSION
        );
    }

    @Override
    public int compareTo(AllocationCycleTrace other) {
        return cycleId.compareTo(
                AllocationValidation.required(other, "other").cycleId
        );
    }

    private static String calculateDigest(
            AllocationCycleId cycleId,
            long simulationTick,
            List<AllocationCyclePhaseRecord> phases
    ) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_cycle_trace_v1")
                        .add(cycleId.value())
                        .add(simulationTick);
        phases.stream().sorted().forEach(record -> digest
                .add(record.phase().name())
                .add(record.position())
                .add(record.operationCount())
                .add(record.stateDigest()));
        return digest.finish();
    }

    private static AllocationCycleValidationException invalid(
            AllocationCycleId cycleId,
            String message
    ) {
        return AllocationCycleValidation.failure(
                AllocationCycleFailureCode.INVALID_RESULT,
                AllocationCycleFailureScope.CYCLE,
                cycleId.value(),
                message
        );
    }
}
