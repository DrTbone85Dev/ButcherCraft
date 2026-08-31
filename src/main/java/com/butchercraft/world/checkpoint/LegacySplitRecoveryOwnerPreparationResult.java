package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryOwnerPreparationResult(
        CheckpointOwnerId ownerId,
        Optional<LegacySplitRecoveryPreparedOwnerSnapshot> snapshot,
        List<LegacySplitRecoveryPublicationFailure> failures
) {
    public LegacySplitRecoveryOwnerPreparationResult {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(value -> Objects.requireNonNull(value, "failure"))
                .toList();
        if (snapshot.isPresent() == !failures.isEmpty()) {
            throw new IllegalArgumentException("Owner preparation must contain either a snapshot or failures");
        }
        if (snapshot.isPresent() && !snapshot.orElseThrow().ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("Prepared snapshot belongs to a different owner");
        }
    }

    public static LegacySplitRecoveryOwnerPreparationResult prepared(
            LegacySplitRecoveryPreparedOwnerSnapshot snapshot
    ) {
        return new LegacySplitRecoveryOwnerPreparationResult(
                snapshot.ownerId(),
                Optional.of(snapshot),
                List.of()
        );
    }

    public static LegacySplitRecoveryOwnerPreparationResult failed(
            CheckpointOwnerId ownerId,
            LegacySplitRecoveryPublicationFailure failure
    ) {
        return new LegacySplitRecoveryOwnerPreparationResult(ownerId, Optional.empty(), List.of(failure));
    }

    public boolean successful() {
        return snapshot.isPresent();
    }
}
