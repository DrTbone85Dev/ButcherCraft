package com.butchercraft.world.simulation.scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record SimulationWorkRequest(
        SimulationWorkId id,
        SimulationWorkTypeId typeId,
        SimulationStageId stageId,
        long scheduledTick,
        WorkPriority priority,
        WorkOrigin origin,
        WorkPayload payload,
        RetryPolicy retryPolicy,
        int maximumAttempts,
        OptionalLong expirationTick,
        List<WorkReference> references
) {
    public SimulationWorkRequest {
        id = Objects.requireNonNull(id, "id");
        typeId = Objects.requireNonNull(typeId, "typeId");
        stageId = Objects.requireNonNull(stageId, "stageId");
        scheduledTick = SchedulerValidation.requireTick(scheduledTick, "Scheduled work tick");
        priority = Objects.requireNonNull(priority, "priority");
        origin = Objects.requireNonNull(origin, "origin");
        payload = Objects.requireNonNull(payload, "payload");
        retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (maximumAttempts <= 0) throw new IllegalArgumentException("Maximum attempts must be positive");
        expirationTick = Objects.requireNonNull(expirationTick, "expirationTick");
        long validatedScheduledTick = scheduledTick;
        expirationTick.ifPresent(value -> {
            if (value < validatedScheduledTick) {
                throw new IllegalArgumentException("Expiration tick precedes scheduled tick");
            }
        });
        references = Objects.requireNonNull(references, "references").stream()
                .map(reference -> Objects.requireNonNull(reference, "reference")).sorted().toList();
        if (references.size() > 32 || new HashSet<>(references).size() != references.size()) {
            throw new IllegalArgumentException("Work references must be unique and limited to 32");
        }
        references = List.copyOf(references);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private SimulationWorkId id;
        private SimulationWorkTypeId typeId;
        private SimulationStageId stageId;
        private long scheduledTick;
        private WorkPriority priority = WorkPriority.NORMAL;
        private WorkOrigin origin;
        private WorkPayload payload = WorkPayload.empty();
        private RetryPolicy retryPolicy = RetryPolicy.never();
        private int maximumAttempts = 1;
        private OptionalLong expirationTick = OptionalLong.empty();
        private List<WorkReference> references = List.of();
        private Builder() { }
        public Builder id(SimulationWorkId value) { id = value; return this; }
        public Builder typeId(SimulationWorkTypeId value) { typeId = value; return this; }
        public Builder stageId(SimulationStageId value) { stageId = value; return this; }
        public Builder scheduledTick(long value) { scheduledTick = value; return this; }
        public Builder priority(WorkPriority value) { priority = value; return this; }
        public Builder origin(WorkOrigin value) { origin = value; return this; }
        public Builder payload(WorkPayload value) { payload = value; return this; }
        public Builder retryPolicy(RetryPolicy value) { retryPolicy = value; return this; }
        public Builder maximumAttempts(int value) { maximumAttempts = value; return this; }
        public Builder expirationTick(long value) { expirationTick = OptionalLong.of(value); return this; }
        public Builder references(List<WorkReference> value) { references = value; return this; }
        public SimulationWorkRequest build() {
            return new SimulationWorkRequest(
                    id, typeId, stageId, scheduledTick, priority, origin, payload, retryPolicy,
                    maximumAttempts, expirationTick, references
            );
        }
    }
}
