package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryDryRunPreview(
        SplitSnapshotRecoveryPlan plan,
        Optional<CheckpointGenerationId> intendedGenerationId,
        Optional<CheckpointGenerationId> predecessorGenerationId,
        List<CheckpointOwnerId> participantOwners,
        RecoveryMutationGate mutationGate,
        boolean protectedPathRejected,
        boolean publicationEligible,
        List<LegacySplitRecoveryPublicationFailure> failures
) {
    public LegacySplitRecoveryDryRunPreview {
        plan = Objects.requireNonNull(plan, "plan");
        intendedGenerationId = Objects.requireNonNull(intendedGenerationId, "intendedGenerationId");
        predecessorGenerationId = Objects.requireNonNull(predecessorGenerationId, "predecessorGenerationId");
        participantOwners = Objects.requireNonNull(participantOwners, "participantOwners").stream().sorted().toList();
        mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
        failures = Objects.requireNonNull(failures, "failures").stream().sorted().toList();
        if (publicationEligible && (!failures.isEmpty() || protectedPathRejected || intendedGenerationId.isEmpty())) {
            throw new IllegalArgumentException("Eligible dry run cannot contain publication blockers");
        }
    }
}
