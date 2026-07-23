package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record ScheduledSimulationWork(
        SimulationWorkId id,
        SimulationWorkTypeId typeId,
        SimulationStageId stageId,
        long scheduledTick,
        WorkPriority priority,
        WorkOrigin origin,
        WorkPayload payload,
        RetryPolicy retryPolicy,
        int maximumAttempts,
        long authoritativeSubmissionSequence,
        OptionalLong expirationTick,
        List<WorkReference> references,
        int generationDepth,
        int schemaVersion
) {
    public ScheduledSimulationWork {
        SimulationWorkRequest request = new SimulationWorkRequest(
                id, typeId, stageId, scheduledTick, priority, origin, payload, retryPolicy,
                maximumAttempts, expirationTick, references
        );
        id = request.id(); typeId = request.typeId(); stageId = request.stageId();
        scheduledTick = request.scheduledTick(); priority = request.priority(); origin = request.origin();
        payload = request.payload(); retryPolicy = request.retryPolicy(); maximumAttempts = request.maximumAttempts();
        expirationTick = request.expirationTick(); references = request.references();
        if (authoritativeSubmissionSequence < 0L) {
            throw new IllegalArgumentException("Submission sequence must not be negative");
        }
        if (generationDepth < 0) throw new IllegalArgumentException("Generation depth must not be negative");
        schemaVersion = SchedulerValidation.requireSchema(schemaVersion, "scheduled work");
    }

    static ScheduledSimulationWork fromRequest(
            SimulationWorkRequest request, long sequence, int generationDepth
    ) {
        Objects.requireNonNull(request, "request");
        return new ScheduledSimulationWork(
                request.id(), request.typeId(), request.stageId(), request.scheduledTick(), request.priority(),
                request.origin(), request.payload(), request.retryPolicy(), request.maximumAttempts(), sequence,
                request.expirationTick(), request.references(), generationDepth, SchedulerSchema.CURRENT_VERSION
        );
    }
}
