package com.butchercraft.integration.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.RecoveryMutationGate;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record LiveOwnerCoherenceReport(
        LiveOwnerCoherenceStatus status,
        OptionalLong clockTick,
        OptionalLong schedulerTick,
        List<CheckpointOwnerId> ownersInspected,
        List<StartupRecoveryIssue> issues,
        RecoveryMutationGate mutationGate,
        long elapsedNanos
) {
    public LiveOwnerCoherenceReport {
        status = Objects.requireNonNull(status, "status");
        clockTick = Objects.requireNonNull(clockTick, "clockTick");
        schedulerTick = Objects.requireNonNull(schedulerTick, "schedulerTick");
        ownersInspected = Objects.requireNonNull(ownersInspected, "ownersInspected").stream()
                .distinct().sorted().toList();
        issues = Objects.requireNonNull(issues, "issues").stream().sorted().toList();
        mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
        if (elapsedNanos < 0L) throw new IllegalArgumentException("Coherence elapsed time is negative");
        if ((status == LiveOwnerCoherenceStatus.INCOHERENT) != !issues.isEmpty()) {
            throw new IllegalArgumentException("Live coherence status and issues disagree");
        }
    }

    public boolean coherent() {
        return status == LiveOwnerCoherenceStatus.COHERENT
                || status == LiveOwnerCoherenceStatus.COHERENT_EMPTY;
    }

    public long analysisDurationNanos() {
        return elapsedNanos;
    }

    public String issueSummary() {
        return issues.isEmpty()
                ? "none"
                : issues.stream().map(issue -> issue.code().name() + ": " + issue.detail())
                .collect(java.util.stream.Collectors.joining("; "));
    }
}
