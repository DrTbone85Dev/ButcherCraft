package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record CheckpointIntegrityResult(List<CheckpointFailure> failures) {
    public CheckpointIntegrityResult {
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        failures.forEach(failure -> Objects.requireNonNull(failure, "failure"));
    }

    public static CheckpointIntegrityResult successful() {
        return new CheckpointIntegrityResult(List.of());
    }

    public static CheckpointIntegrityResult failed(List<CheckpointFailure> failures) {
        return new CheckpointIntegrityResult(failures);
    }

    public boolean successfulResult() {
        return failures.isEmpty();
    }
}
