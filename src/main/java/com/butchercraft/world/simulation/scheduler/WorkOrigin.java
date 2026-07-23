package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;
import java.util.Optional;

public record WorkOrigin(
        String sourceSubsystemId,
        Optional<String> sourceReferenceType,
        Optional<String> sourceReferenceId,
        long submissionTick,
        String submittingAuthority,
        Optional<String> correlationId,
        Optional<SimulationWorkId> parentWorkId
) {
    public WorkOrigin {
        sourceSubsystemId = SchedulerValidation.requireId(sourceSubsystemId, "Origin subsystem id");
        sourceReferenceType = normalizeOptionalId(sourceReferenceType, "Origin reference type");
        sourceReferenceId = normalizeOptionalId(sourceReferenceId, "Origin reference id");
        if (sourceReferenceType.isPresent() != sourceReferenceId.isPresent()) {
            throw new IllegalArgumentException("Origin reference type and id must be supplied together");
        }
        submissionTick = SchedulerValidation.requireTick(submissionTick, "Origin submission tick");
        submittingAuthority = SchedulerValidation.requireId(submittingAuthority, "Submitting authority");
        correlationId = normalizeOptionalId(correlationId, "Origin correlation id");
        parentWorkId = Objects.requireNonNull(parentWorkId, "parentWorkId");
    }

    public static WorkOrigin of(String subsystemId, long tick, String authority) {
        return new WorkOrigin(
                subsystemId, Optional.empty(), Optional.empty(), tick, authority, Optional.empty(), Optional.empty()
        );
    }

    public WorkOrigin withParent(SimulationWorkId parent, long tick) {
        return new WorkOrigin(
                sourceSubsystemId, sourceReferenceType, sourceReferenceId, tick, submittingAuthority,
                correlationId, Optional.of(parent)
        );
    }

    private static Optional<String> normalizeOptionalId(Optional<String> source, String label) {
        return Objects.requireNonNull(source, label).map(value -> SchedulerValidation.requireId(value, label));
    }
}
