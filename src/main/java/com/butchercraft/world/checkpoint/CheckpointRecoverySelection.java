package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointRecoverySelection(
        CheckpointRecoveryOutcome outcome,
        Optional<CheckpointGenerationId> selectedGenerationId,
        Optional<String> selectedManifestDigest,
        List<CheckpointFailure> diagnostics
) {
    public CheckpointRecoverySelection {
        outcome = Objects.requireNonNull(outcome, "outcome");
        selectedGenerationId = Objects.requireNonNull(selectedGenerationId, "selectedGenerationId");
        selectedManifestDigest = CheckpointValidation.optionalDigest(
                selectedManifestDigest,
                "selectedManifestDigest"
        );
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        diagnostics.forEach(diagnostic -> Objects.requireNonNull(diagnostic, "diagnostic"));
    }

    public static CheckpointRecoverySelection selected(
            CheckpointRecoveryOutcome outcome,
            CheckpointGenerationManifest manifest,
            List<CheckpointFailure> diagnostics
    ) {
        return new CheckpointRecoverySelection(
                outcome,
                Optional.of(manifest.generationId()),
                Optional.of(manifest.manifestDigest()),
                diagnostics
        );
    }

    public static CheckpointRecoverySelection blocked(List<CheckpointFailure> diagnostics) {
        return new CheckpointRecoverySelection(
                CheckpointRecoveryOutcome.RECOVERY_BLOCKED,
                Optional.empty(),
                Optional.empty(),
                diagnostics
        );
    }
}
