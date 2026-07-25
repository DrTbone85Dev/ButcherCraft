package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record CheckpointOwnerRestorationPublicationResult(
        CheckpointOwnerId ownerId,
        List<CheckpointFailure> failures
) {
    public CheckpointOwnerRestorationPublicationResult {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
    }

    public static CheckpointOwnerRestorationPublicationResult published(CheckpointOwnerId ownerId) {
        return new CheckpointOwnerRestorationPublicationResult(ownerId, List.of());
    }

    public static CheckpointOwnerRestorationPublicationResult failed(
            CheckpointOwnerId ownerId,
            List<CheckpointFailure> failures
    ) {
        return new CheckpointOwnerRestorationPublicationResult(ownerId, failures);
    }

    public boolean successful() {
        return failures.isEmpty();
    }
}
