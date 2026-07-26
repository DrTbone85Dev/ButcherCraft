package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointPublicationReport(
        CheckpointPublicationOutcome outcome,
        Optional<CheckpointGenerationManifest> generationManifest,
        Optional<CheckpointHeadRecord> headRecord,
        List<CheckpointFailure> diagnostics,
        List<CheckpointStorageArtifact> artifacts
) {
    public CheckpointPublicationReport {
        outcome = Objects.requireNonNull(outcome, "outcome");
        generationManifest = Objects.requireNonNull(generationManifest, "generationManifest");
        headRecord = Objects.requireNonNull(headRecord, "headRecord");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics").stream()
                .map(diagnostic -> Objects.requireNonNull(diagnostic, "diagnostic"))
                .toList();
        artifacts = Objects.requireNonNull(artifacts, "artifacts").stream()
                .map(artifact -> Objects.requireNonNull(artifact, "artifact"))
                .sorted()
                .toList();
    }

    public boolean successful() {
        return outcome == CheckpointPublicationOutcome.PUBLISHED
                || outcome == CheckpointPublicationOutcome.DUPLICATE_OBSERVATION;
    }

    public static CheckpointPublicationReport published(
            CheckpointGenerationManifest manifest,
            CheckpointHeadRecord head,
            List<CheckpointFailure> diagnostics
    ) {
        return new CheckpointPublicationReport(
                CheckpointPublicationOutcome.PUBLISHED,
                Optional.of(manifest),
                Optional.of(head),
                diagnostics,
                List.of()
        );
    }

    public static CheckpointPublicationReport duplicate(CheckpointGenerationManifest manifest) {
        return new CheckpointPublicationReport(
                CheckpointPublicationOutcome.DUPLICATE_OBSERVATION,
                Optional.of(manifest),
                Optional.empty(),
                List.of(),
                List.of()
        );
    }

    public static CheckpointPublicationReport failed(
            CheckpointPublicationOutcome outcome,
            List<CheckpointFailure> diagnostics,
            List<CheckpointStorageArtifact> artifacts
    ) {
        if (outcome == CheckpointPublicationOutcome.PUBLISHED
                || outcome == CheckpointPublicationOutcome.DUPLICATE_OBSERVATION) {
            throw new IllegalArgumentException("Failure report requires a non-success outcome: " + outcome);
        }
        return new CheckpointPublicationReport(outcome, Optional.empty(), Optional.empty(), diagnostics, artifacts);
    }
}
