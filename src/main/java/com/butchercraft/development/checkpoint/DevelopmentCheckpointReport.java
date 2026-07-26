package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointCoordinatedRestorationReport;
import com.butchercraft.world.checkpoint.CheckpointFailure;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryReport;
import com.butchercraft.world.checkpoint.CheckpointHeadRecord;
import com.butchercraft.world.checkpoint.CheckpointPublicationReport;
import com.butchercraft.world.checkpoint.CheckpointStorageArtifact;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DevelopmentCheckpointReport(
        DevelopmentCheckpointOperation operation,
        boolean successful,
        Optional<Path> checkpointRoot,
        Optional<CheckpointPublicationReport> publicationReport,
        Optional<CheckpointFilesystemRecoveryReport> recoveryReport,
        Optional<CheckpointCoordinatedRestorationReport> restorationReport,
        Optional<DevelopmentCheckpointGenerationSummary> selectedGeneration,
        List<DevelopmentCheckpointGenerationSummary> generations,
        List<CheckpointHeadRecord> heads,
        List<CheckpointStorageArtifact> artifacts,
        List<CheckpointFailure> checkpointFailures,
        List<DevelopmentCheckpointFailure> failures,
        List<String> warnings
) {
    public DevelopmentCheckpointReport {
        operation = Objects.requireNonNull(operation, "operation");
        checkpointRoot = Objects.requireNonNull(checkpointRoot, "checkpointRoot")
                .map(root -> root.toAbsolutePath().normalize());
        publicationReport = Objects.requireNonNull(publicationReport, "publicationReport");
        recoveryReport = Objects.requireNonNull(recoveryReport, "recoveryReport");
        restorationReport = Objects.requireNonNull(restorationReport, "restorationReport");
        selectedGeneration = Objects.requireNonNull(selectedGeneration, "selectedGeneration");
        generations = Objects.requireNonNull(generations, "generations").stream()
                .map(generation -> Objects.requireNonNull(generation, "generation"))
                .sorted()
                .toList();
        heads = Objects.requireNonNull(heads, "heads").stream()
                .map(head -> Objects.requireNonNull(head, "head"))
                .sorted()
                .toList();
        artifacts = Objects.requireNonNull(artifacts, "artifacts").stream()
                .map(artifact -> Objects.requireNonNull(artifact, "artifact"))
                .sorted()
                .toList();
        checkpointFailures = Objects.requireNonNull(checkpointFailures, "checkpointFailures").stream()
                .map(failure -> Objects.requireNonNull(failure, "checkpointFailure"))
                .toList();
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .sorted()
                .toList();
        warnings = Objects.requireNonNull(warnings, "warnings").stream()
                .map(warning -> clean(warning, "warning"))
                .sorted()
                .toList();
    }

    public static DevelopmentCheckpointReport blocked(
            DevelopmentCheckpointOperation operation,
            Optional<Path> checkpointRoot,
            DevelopmentCheckpointFailure failure
    ) {
        return new DevelopmentCheckpointReport(
                operation,
                false,
                checkpointRoot,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(failure),
                List.of()
        );
    }

    private static String clean(String value, String field) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return cleaned;
    }
}
