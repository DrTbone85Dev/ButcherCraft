package com.butchercraft.world.checkpoint;

import com.butchercraft.integration.checkpoint.LiveOwnerCoherenceStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record StartupRecoveryStatus(
        StartupRecoveryState state,
        LiveOwnerCoherenceStatus liveCoherence,
        StartupRecoverySource selectedSource,
        Optional<CheckpointGenerationId> selectedGeneration,
        Optional<CheckpointGenerationId> previousValidGeneration,
        Optional<String> workstationRestorabilityStatus,
        Optional<RestorationIdentity> restorationIdentity,
        int completedParticipants,
        OptionalLong restoredSimulationTick,
        List<String> remainingAuthorityBlocks,
        List<String> policyBRuns,
        List<String> preservedAuthorizedWork,
        Optional<String> fallbackReason,
        Optional<String> lastRestorationResultIdentity,
        Optional<StartupRecoveryFailureCode> failureCode,
        boolean consequentialMutationPermitted,
        Optional<String> operatorActionRequired,
        long liveAnalysisNanos,
        long checkpointSelectionNanos,
        long restorationNanos,
        long totalNanos
) {
    public StartupRecoveryStatus {
        state = Objects.requireNonNull(state, "state");
        liveCoherence = Objects.requireNonNull(liveCoherence, "liveCoherence");
        selectedSource = Objects.requireNonNull(selectedSource, "selectedSource");
        selectedGeneration = Objects.requireNonNull(selectedGeneration, "selectedGeneration");
        previousValidGeneration = Objects.requireNonNull(previousValidGeneration, "previousValidGeneration");
        workstationRestorabilityStatus = Objects.requireNonNull(
                workstationRestorabilityStatus, "workstationRestorabilityStatus");
        restorationIdentity = Objects.requireNonNull(restorationIdentity, "restorationIdentity");
        if (completedParticipants < 0) throw new IllegalArgumentException("Participant count must not be negative");
        restoredSimulationTick = Objects.requireNonNull(restoredSimulationTick, "restoredSimulationTick");
        remainingAuthorityBlocks = List.copyOf(Objects.requireNonNull(
                remainingAuthorityBlocks, "remainingAuthorityBlocks"));
        policyBRuns = List.copyOf(Objects.requireNonNull(policyBRuns, "policyBRuns"));
        preservedAuthorizedWork = List.copyOf(Objects.requireNonNull(
                preservedAuthorizedWork, "preservedAuthorizedWork"));
        fallbackReason = Objects.requireNonNull(fallbackReason, "fallbackReason");
        lastRestorationResultIdentity = Objects.requireNonNull(
                lastRestorationResultIdentity, "lastRestorationResultIdentity");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        operatorActionRequired = Objects.requireNonNull(operatorActionRequired, "operatorActionRequired");
        if (liveAnalysisNanos < 0L || checkpointSelectionNanos < 0L
                || restorationNanos < 0L || totalNanos < 0L) {
            throw new IllegalArgumentException("Startup recovery durations must not be negative");
        }
    }

    public static StartupRecoveryStatus notStarted() {
        return new StartupRecoveryStatus(
                StartupRecoveryState.NOT_STARTED,
                LiveOwnerCoherenceStatus.NOT_ANALYZED,
                StartupRecoverySource.NONE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                OptionalLong.empty(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.of("Wait for the startup recovery decision before consequential mutation"),
                0L, 0L, 0L, 0L
        );
    }
}
