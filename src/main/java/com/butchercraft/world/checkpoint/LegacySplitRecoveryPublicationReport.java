package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryPublicationReport(
        Outcome outcome,
        Optional<SplitSnapshotRecoveryPlan> reanalyzedPlan,
        Optional<LegacySplitRecoveryResult> recoveryResult,
        List<LegacySplitRecoveryPublicationFailure> failures
) {
    public LegacySplitRecoveryPublicationReport {
        outcome = Objects.requireNonNull(outcome, "outcome");
        reanalyzedPlan = Objects.requireNonNull(reanalyzedPlan, "reanalyzedPlan");
        recoveryResult = Objects.requireNonNull(recoveryResult, "recoveryResult");
        failures = Objects.requireNonNull(failures, "failures").stream().sorted().toList();
        boolean success = outcome == Outcome.COMMITTED || outcome == Outcome.EXISTING_RESULT_OBSERVED;
        if (success != recoveryResult.isPresent()) {
            throw new IllegalArgumentException("Successful recovery publication requires one exact result");
        }
    }

    public static LegacySplitRecoveryPublicationReport committed(
            SplitSnapshotRecoveryPlan plan,
            LegacySplitRecoveryResult result,
            boolean existing
    ) {
        return new LegacySplitRecoveryPublicationReport(
                existing ? Outcome.EXISTING_RESULT_OBSERVED : Outcome.COMMITTED,
                Optional.of(plan),
                Optional.of(result),
                List.of()
        );
    }

    public static LegacySplitRecoveryPublicationReport failed(
            Outcome outcome,
            SplitSnapshotRecoveryPlan plan,
            List<LegacySplitRecoveryPublicationFailure> failures
    ) {
        if (outcome == Outcome.COMMITTED || outcome == Outcome.EXISTING_RESULT_OBSERVED) {
            throw new IllegalArgumentException("Failure report requires a non-success outcome");
        }
        return new LegacySplitRecoveryPublicationReport(
                outcome,
                Optional.ofNullable(plan),
                Optional.empty(),
                failures
        );
    }

    public boolean successful() {
        return outcome == Outcome.COMMITTED || outcome == Outcome.EXISTING_RESULT_OBSERVED;
    }

    public enum Outcome {
        COMMITTED,
        EXISTING_RESULT_OBSERVED,
        AUTHORIZATION_REJECTED,
        RECOVERY_BLOCKED,
        PUBLICATION_FAILED,
        PUBLICATION_INTERRUPTED
    }
}
