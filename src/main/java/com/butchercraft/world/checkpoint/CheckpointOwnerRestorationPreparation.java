package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointOwnerRestorationPreparation(
        CheckpointOwnerId ownerId,
        Optional<CheckpointOwnerRestorationCandidate> candidate,
        List<CheckpointFailure> failures
) {
    public CheckpointOwnerRestorationPreparation {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        candidate = Objects.requireNonNull(candidate, "candidate");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
        if (candidate.isPresent() && !candidate.get().ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("Restoration candidate owner does not match preparation owner");
        }
        if (candidate.isPresent() == !failures.isEmpty()) {
            throw new IllegalArgumentException("Restoration preparation must contain either a candidate or failures");
        }
    }

    public static CheckpointOwnerRestorationPreparation prepared(
            CheckpointOwnerRestorationCandidate candidate
    ) {
        return new CheckpointOwnerRestorationPreparation(
                Objects.requireNonNull(candidate, "candidate").ownerId(),
                Optional.of(candidate),
                List.of()
        );
    }

    public static CheckpointOwnerRestorationPreparation failed(
            CheckpointOwnerId ownerId,
            List<CheckpointFailure> failures
    ) {
        return new CheckpointOwnerRestorationPreparation(ownerId, Optional.empty(), failures);
    }

    public boolean successful() {
        return candidate.isPresent();
    }
}
