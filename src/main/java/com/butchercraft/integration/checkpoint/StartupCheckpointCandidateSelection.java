package com.butchercraft.integration.checkpoint;

import com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityReport;
import com.butchercraft.world.checkpoint.CheckpointHeadRecord;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointRecoveredGeneration;
import com.butchercraft.world.checkpoint.CheckpointRecoveredGenerationReport;
import com.butchercraft.world.checkpoint.StartupRecoveryFailureCode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StartupCheckpointCandidateSelection(
        Optional<CheckpointRecoveredGeneration> generation,
        Optional<CheckpointHeadRecord> sourceHead,
        Optional<WorkstationCheckpointRestorabilityReport> workstationRestorability,
        Optional<CheckpointRecoveredGenerationReport> recoveredGenerationReport,
        Optional<CheckpointGenerationId> previousValidGeneration,
        List<String> rejectedCandidates,
        StartupRecoveryFailureCode failureCode
) {
    public StartupCheckpointCandidateSelection {
        generation = Objects.requireNonNull(generation, "generation");
        sourceHead = Objects.requireNonNull(sourceHead, "sourceHead");
        workstationRestorability = Objects.requireNonNull(
                workstationRestorability, "workstationRestorability");
        recoveredGenerationReport = Objects.requireNonNull(
                recoveredGenerationReport, "recoveredGenerationReport");
        previousValidGeneration = Objects.requireNonNull(previousValidGeneration, "previousValidGeneration");
        rejectedCandidates = Objects.requireNonNull(rejectedCandidates, "rejectedCandidates").stream()
                .map(value -> Objects.requireNonNull(value, "rejectedCandidate").strip())
                .filter(value -> !value.isEmpty())
                .toList();
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        boolean selected = generation.isPresent();
        if (selected != sourceHead.isPresent()
                || selected != workstationRestorability.isPresent()
                || selected != recoveredGenerationReport.isPresent()) {
            throw new IllegalArgumentException("Startup checkpoint selection evidence is incomplete");
        }
    }

    public boolean successful() {
        return generation.isPresent();
    }

    public String rejectionSummary() {
        return rejectedCandidates.isEmpty() ? "none" : String.join("; ", rejectedCandidates);
    }
}
