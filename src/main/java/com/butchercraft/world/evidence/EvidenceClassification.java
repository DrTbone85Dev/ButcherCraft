package com.butchercraft.world.evidence;

import java.util.Objects;

public record EvidenceClassification(EvidenceClass primaryClass)
        implements Comparable<EvidenceClassification> {
    public EvidenceClassification {
        primaryClass = Objects.requireNonNull(primaryClass, "primaryClass");
    }

    public static EvidenceClassification of(EvidenceClass primaryClass) {
        return new EvidenceClassification(primaryClass);
    }

    public boolean protectsAuthoritativeFact() {
        return primaryClass == EvidenceClass.AUTHORITATIVE_RUNTIME;
    }

    public boolean protectsPermanentAudit() {
        return primaryClass == EvidenceClass.PERMANENT_AUDIT;
    }

    public boolean protectsReplay() {
        return primaryClass == EvidenceClass.REPLAY_CRITICAL;
    }

    public boolean isDerivedSummaryIndex() {
        return primaryClass == EvidenceClass.DERIVED_SUMMARY_INDEX;
    }

    public boolean isDisposableDiagnostic() {
        return primaryClass == EvidenceClass.DISPOSABLE_DIAGNOSTIC;
    }

    @Override
    public int compareTo(EvidenceClassification other) {
        return primaryClass.compareTo(Objects.requireNonNull(other, "other").primaryClass);
    }
}
