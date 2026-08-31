package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record LiveCheckpointStatus(
        State state,
        Optional<CheckpointGenerationId> currentGeneration,
        Optional<CheckpointGenerationId> committedGeneration,
        Optional<CheckpointGenerationId> previousValidGeneration,
        long checkpointTick,
        int participantCount,
        Set<LiveCheckpointTriggerCause> causes,
        long freezeDurationNanos,
        long publicationDurationNanos,
        long headCommitDurationNanos,
        long totalDurationNanos,
        long checkpointSizeBytes,
        long nextPeriodicTick,
        boolean automaticCadenceEnabled,
        List<CheckpointFailure> failures
) {
    public LiveCheckpointStatus {
        state = Objects.requireNonNull(state, "state");
        currentGeneration = Objects.requireNonNull(currentGeneration, "currentGeneration");
        committedGeneration = Objects.requireNonNull(committedGeneration, "committedGeneration");
        previousValidGeneration = Objects.requireNonNull(previousValidGeneration, "previousValidGeneration");
        checkpointTick = CheckpointValidation.nonNegative(checkpointTick, "checkpointTick");
        if (participantCount < 0) {
            throw new IllegalArgumentException("participantCount must not be negative");
        }
        causes = Set.copyOf(Objects.requireNonNull(causes, "causes"));
        freezeDurationNanos = CheckpointValidation.nonNegative(freezeDurationNanos, "freezeDurationNanos");
        publicationDurationNanos = CheckpointValidation.nonNegative(
                publicationDurationNanos,
                "publicationDurationNanos"
        );
        headCommitDurationNanos = CheckpointValidation.nonNegative(
                headCommitDurationNanos,
                "headCommitDurationNanos"
        );
        totalDurationNanos = CheckpointValidation.nonNegative(totalDurationNanos, "totalDurationNanos");
        checkpointSizeBytes = CheckpointValidation.nonNegative(checkpointSizeBytes, "checkpointSizeBytes");
        nextPeriodicTick = CheckpointValidation.nonNegative(nextPeriodicTick, "nextPeriodicTick");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }

    public static LiveCheckpointStatus inactive() {
        return new LiveCheckpointStatus(
                State.NOT_INITIALIZED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0L,
                0,
                Set.of(),
                0L,
                0L,
                0L,
                0L,
                0L,
                LiveCheckpointPolicy.PERIODIC_INTERVAL_TICKS,
                true,
                List.of()
        );
    }

    public enum State {
        NOT_INITIALIZED,
        IDLE,
        REQUESTED,
        FREEZING,
        PUBLISHING,
        COMMITTED,
        FAILED
    }
}
