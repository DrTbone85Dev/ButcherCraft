package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record CheckpointFilesystemRecoveryReport(
        CheckpointRecoverySelection selection,
        List<CheckpointGenerationRecord> generationRecords,
        List<CheckpointHeadRecord> headRecords,
        List<CheckpointStorageArtifact> artifacts
) {
    public CheckpointFilesystemRecoveryReport {
        selection = Objects.requireNonNull(selection, "selection");
        generationRecords = Objects.requireNonNull(generationRecords, "generationRecords").stream()
                .map(record -> Objects.requireNonNull(record, "generationRecord"))
                .sorted()
                .toList();
        headRecords = Objects.requireNonNull(headRecords, "headRecords").stream()
                .map(head -> Objects.requireNonNull(head, "headRecord"))
                .sorted()
                .toList();
        artifacts = Objects.requireNonNull(artifacts, "artifacts").stream()
                .map(artifact -> Objects.requireNonNull(artifact, "artifact"))
                .sorted()
                .toList();
    }
}
