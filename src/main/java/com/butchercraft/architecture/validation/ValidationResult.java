package com.butchercraft.architecture.validation;

import java.util.List;
import java.util.Objects;

public record ValidationResult(
        ArchitectureId ruleId,
        ValidationCategory category,
        ValidationSeverity severity,
        ValidationStatus status,
        String message,
        List<String> details
) {
    public ValidationResult {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
        message = requireText(message, "message");
        details = Objects.requireNonNull(details, "details").stream()
                .map(detail -> requireText(detail, "detail"))
                .sorted()
                .toList();
        if (status == ValidationStatus.PASSED && !details.isEmpty()) {
            throw new IllegalArgumentException("Passed validation results cannot contain violation details");
        }
        if (status == ValidationStatus.FAILED && severity != ValidationSeverity.ERROR) {
            throw new IllegalArgumentException("Failed validation results require ERROR severity");
        }
        if (status == ValidationStatus.WARNING && severity != ValidationSeverity.WARNING) {
            throw new IllegalArgumentException("Warning validation results require WARNING severity");
        }
        if (status == ValidationStatus.INFORMATIONAL && severity != ValidationSeverity.INFORMATION) {
            throw new IllegalArgumentException("Informational validation results require INFORMATION severity");
        }
    }

    public static ValidationResult passed(ValidationRule rule, String message) {
        Objects.requireNonNull(rule, "rule");
        return new ValidationResult(
                rule.id(), rule.category(), rule.severity(), ValidationStatus.PASSED, message, List.of()
        );
    }

    public static ValidationResult issue(ValidationRule rule, String message, List<String> details) {
        Objects.requireNonNull(rule, "rule");
        return new ValidationResult(
                rule.id(),
                rule.category(),
                rule.severity(),
                statusFor(rule.severity()),
                message,
                details
        );
    }

    public static ValidationResult malformedRule(ValidationRule rule, RuntimeException failure) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(failure, "failure");
        String failureMessage = failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + failure.getMessage().strip();
        return new ValidationResult(
                rule.id(),
                rule.category(),
                ValidationSeverity.ERROR,
                ValidationStatus.FAILED,
                "Validation rule execution failed",
                List.of(failureMessage)
        );
    }

    public boolean isSuccessful() {
        return status != ValidationStatus.FAILED;
    }

    private static ValidationStatus statusFor(ValidationSeverity severity) {
        return switch (severity) {
            case ERROR -> ValidationStatus.FAILED;
            case WARNING -> ValidationStatus.WARNING;
            case INFORMATION -> ValidationStatus.INFORMATIONAL;
        };
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
