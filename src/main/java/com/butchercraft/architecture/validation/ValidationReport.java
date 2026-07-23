package com.butchercraft.architecture.validation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record ValidationReport(
        ArchitectureId contextId,
        List<ValidationResult> results,
        ValidationSummary summary
) {
    public ValidationReport {
        Objects.requireNonNull(contextId, "contextId");
        results = Objects.requireNonNull(results, "results").stream()
                .map(result -> Objects.requireNonNull(result, "result"))
                .toList();
        Objects.requireNonNull(summary, "summary");
        if (summary.ruleCount() != results.size()) {
            throw new IllegalArgumentException("Summary rule count must match validation results");
        }
    }

    public static ValidationReport of(
            ArchitectureId contextId,
            List<ValidationResult> results,
            Duration executionTime
    ) {
        return new ValidationReport(
                contextId,
                results,
                ValidationSummary.from(results, executionTime)
        );
    }

    public boolean successful() {
        return summary.successful();
    }

    public List<ValidationResult> passedRules() {
        return byStatus(ValidationStatus.PASSED);
    }

    public List<ValidationResult> failedRules() {
        return byStatus(ValidationStatus.FAILED);
    }

    public List<ValidationResult> warnings() {
        return byStatus(ValidationStatus.WARNING);
    }

    public List<ValidationResult> informationalMessages() {
        return byStatus(ValidationStatus.INFORMATIONAL);
    }

    public List<ValidationResult> findByCategory(ValidationCategory category) {
        Objects.requireNonNull(category, "category");
        return results.stream().filter(result -> result.category() == category).toList();
    }

    private List<ValidationResult> byStatus(ValidationStatus status) {
        return results.stream().filter(result -> result.status() == status).toList();
    }
}
