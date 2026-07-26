package com.butchercraft.world.evidence;

import java.util.List;
import java.util.Objects;

public record EvidenceRetentionDecision(
        EvidenceLifecycleDisposition disposition,
        List<EvidenceRetentionReason> reasons,
        List<EvidenceLifecycleFailure> failures
) {
    public EvidenceRetentionDecision {
        disposition = Objects.requireNonNull(disposition, "disposition");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        reasons.forEach(reason -> Objects.requireNonNull(reason, "reason"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        failures.forEach(failure -> Objects.requireNonNull(failure, "failure"));
        if (disposition == EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES
                && failures.isEmpty()) {
            throw new IllegalArgumentException("Blocked lifecycle decisions require explicit failures");
        }
        if (disposition != EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES
                && !failures.isEmpty()) {
            throw new IllegalArgumentException("Successful lifecycle decisions cannot contain failures");
        }
    }

    public static EvidenceRetentionDecision allowed(
            EvidenceLifecycleDisposition disposition,
            List<EvidenceRetentionReason> reasons
    ) {
        return new EvidenceRetentionDecision(disposition, reasons, List.of());
    }

    public static EvidenceRetentionDecision blocked(
            List<EvidenceRetentionReason> reasons,
            List<EvidenceLifecycleFailure> failures
    ) {
        return new EvidenceRetentionDecision(
                EvidenceLifecycleDisposition.LIFECYCLE_BLOCKED_BY_MISSING_GUARANTEES,
                reasons,
                failures
        );
    }

    public boolean successful() {
        return failures.isEmpty();
    }
}
