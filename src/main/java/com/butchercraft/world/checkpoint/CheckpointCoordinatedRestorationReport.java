package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointCoordinatedRestorationReport(
        CheckpointCoordinatedRestorationOutcome outcome,
        Optional<CheckpointGenerationId> generationId,
        List<CheckpointOwnerId> preparedOwners,
        List<CheckpointOwnerId> publishedOwners,
        List<CheckpointFailure> failures
) {
    public CheckpointCoordinatedRestorationReport {
        outcome = Objects.requireNonNull(outcome, "outcome");
        generationId = Objects.requireNonNull(generationId, "generationId");
        preparedOwners = Objects.requireNonNull(preparedOwners, "preparedOwners").stream()
                .map(owner -> Objects.requireNonNull(owner, "preparedOwner"))
                .sorted()
                .toList();
        publishedOwners = Objects.requireNonNull(publishedOwners, "publishedOwners").stream()
                .map(owner -> Objects.requireNonNull(owner, "publishedOwner"))
                .sorted()
                .toList();
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
        if (outcome == CheckpointCoordinatedRestorationOutcome.RESTORED && !failures.isEmpty()) {
            throw new IllegalArgumentException("Successful restoration must not contain failures");
        }
        if (outcome == CheckpointCoordinatedRestorationOutcome.RECOVERY_BLOCKED && failures.isEmpty()) {
            throw new IllegalArgumentException("Blocked restoration requires failures");
        }
    }

    public static CheckpointCoordinatedRestorationReport restored(
            CheckpointGenerationId generationId,
            List<CheckpointOwnerId> preparedOwners,
            List<CheckpointOwnerId> publishedOwners
    ) {
        return new CheckpointCoordinatedRestorationReport(
                CheckpointCoordinatedRestorationOutcome.RESTORED,
                Optional.of(generationId),
                preparedOwners,
                publishedOwners,
                List.of()
        );
    }

    public static CheckpointCoordinatedRestorationReport blocked(
            Optional<CheckpointGenerationId> generationId,
            List<CheckpointOwnerId> preparedOwners,
            List<CheckpointOwnerId> publishedOwners,
            List<CheckpointFailure> failures
    ) {
        return new CheckpointCoordinatedRestorationReport(
                CheckpointCoordinatedRestorationOutcome.RECOVERY_BLOCKED,
                generationId,
                preparedOwners,
                publishedOwners,
                failures
        );
    }
}
