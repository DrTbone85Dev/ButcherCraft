package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointRollbackDecision(
        CheckpointRecoveryOutcome outcome,
        Optional<CheckpointGenerationId> selectedGenerationId,
        Optional<String> selectedManifestDigest,
        boolean newerHistoryPreserved,
        boolean laterRuntimeMustPublishRecoveryHistory,
        List<CheckpointFailure> failures
) {
    public CheckpointRollbackDecision {
        outcome = Objects.requireNonNull(outcome, "outcome");
        selectedGenerationId = Objects.requireNonNull(selectedGenerationId, "selectedGenerationId");
        selectedManifestDigest = CheckpointValidation.optionalDigest(
                selectedManifestDigest,
                "selectedManifestDigest"
        );
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        failures.forEach(failure -> Objects.requireNonNull(failure, "failure"));
    }
}
