package com.butchercraft.world.checkpoint;

import java.util.Objects;
import java.util.Optional;

public record LiveCheckpointRequestResult(
        LiveCheckpointRequestOutcome outcome,
        Optional<CheckpointGenerationId> activeGeneration,
        String detail
) {
    public LiveCheckpointRequestResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
        activeGeneration = Objects.requireNonNull(activeGeneration, "activeGeneration");
        detail = CheckpointValidation.text(detail, "detail");
    }
}
