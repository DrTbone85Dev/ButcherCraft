package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointRecoveredGenerationReport(
        CheckpointFilesystemRecoveryReport filesystemRecoveryReport,
        Optional<CheckpointRecoveredGeneration> recoveredGeneration,
        List<CheckpointFailure> failures
) {
    public CheckpointRecoveredGenerationReport {
        filesystemRecoveryReport = Objects.requireNonNull(filesystemRecoveryReport, "filesystemRecoveryReport");
        recoveredGeneration = Objects.requireNonNull(recoveredGeneration, "recoveredGeneration");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
        if (recoveredGeneration.isPresent() == !failures.isEmpty()) {
            throw new IllegalArgumentException("Recovered generation report must contain either a generation or failures");
        }
    }

    public static CheckpointRecoveredGenerationReport recovered(
            CheckpointFilesystemRecoveryReport report,
            CheckpointRecoveredGeneration generation
    ) {
        return new CheckpointRecoveredGenerationReport(report, Optional.of(generation), List.of());
    }

    public static CheckpointRecoveredGenerationReport failed(
            CheckpointFilesystemRecoveryReport report,
            List<CheckpointFailure> failures
    ) {
        return new CheckpointRecoveredGenerationReport(report, Optional.empty(), failures);
    }

    public boolean successful() {
        return recoveredGeneration.isPresent();
    }
}
