package com.butchercraft.world.checkpoint;

import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryOwnerPreparationRequest(
        SplitSnapshotRecoveryPlan plan,
        RecoveryOperatorAuthorization authorization,
        CheckpointGenerationId generationId
) {
    public LegacySplitRecoveryOwnerPreparationRequest {
        plan = Objects.requireNonNull(plan, "plan");
        authorization = Objects.requireNonNull(authorization, "authorization");
        generationId = Objects.requireNonNull(generationId, "generationId");
        if (!authorization.targets(plan)) {
            throw new IllegalArgumentException("Owner preparation requires authorization for the exact recovery plan");
        }
        if (generationId.authoritativeSimulationTick() != plan.authoritativeClockTick()) {
            throw new IllegalArgumentException("Recovery generation tick must equal the authoritative Clock tick");
        }
    }

    public Optional<RecoverySourceSnapshot> sourceFor(CheckpointOwnerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return plan.sourceSnapshots().stream().filter(source -> source.ownerId().equals(ownerId)).findFirst();
    }

    public RecoverySourceSnapshot requireSource(CheckpointOwnerId ownerId) {
        return sourceFor(ownerId).orElseThrow(() -> new IllegalArgumentException(
                "Recovery source snapshot is missing for owner " + ownerId.value()
        ));
    }
}
