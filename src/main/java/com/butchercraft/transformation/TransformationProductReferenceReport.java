package com.butchercraft.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of validating transformation product references.
 */
public record TransformationProductReferenceReport(
        List<TransformationProductReferenceIssue> issues
) {
    public static final TransformationProductReferenceReport EMPTY = new TransformationProductReferenceReport(List.of());

    public TransformationProductReferenceReport {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public static TransformationProductReferenceReport combine(List<TransformationProductReferenceReport> reports) {
        List<TransformationProductReferenceIssue> combined = new ArrayList<>();
        for (TransformationProductReferenceReport report : Objects.requireNonNull(reports, "reports")) {
            combined.addAll(Objects.requireNonNull(report, "report").issues());
        }
        return new TransformationProductReferenceReport(combined);
    }

    public boolean succeeded() {
        return issues.isEmpty();
    }

    public boolean hasErrors() {
        return !issues.isEmpty();
    }
}
