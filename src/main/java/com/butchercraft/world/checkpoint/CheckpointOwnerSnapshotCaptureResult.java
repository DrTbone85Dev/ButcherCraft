package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointOwnerSnapshotCaptureResult(
        CheckpointOwnerId ownerId,
        Optional<CheckpointCapturedOwnerSnapshot> snapshot,
        List<CheckpointFailure> failures
) {
    public CheckpointOwnerSnapshotCaptureResult {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
        if (snapshot.isPresent() && !snapshot.get().ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("Captured snapshot owner does not match result owner");
        }
        if (snapshot.isPresent() == !failures.isEmpty()) {
            throw new IllegalArgumentException("Owner capture result must contain either a snapshot or failures");
        }
    }

    public static CheckpointOwnerSnapshotCaptureResult captured(CheckpointCapturedOwnerSnapshot snapshot) {
        return new CheckpointOwnerSnapshotCaptureResult(
                Objects.requireNonNull(snapshot, "snapshot").ownerId(),
                Optional.of(snapshot),
                List.of()
        );
    }

    public static CheckpointOwnerSnapshotCaptureResult failed(
            CheckpointOwnerId ownerId,
            List<CheckpointFailure> failures
    ) {
        return new CheckpointOwnerSnapshotCaptureResult(ownerId, Optional.empty(), failures);
    }

    public boolean successful() {
        return snapshot.isPresent();
    }
}
