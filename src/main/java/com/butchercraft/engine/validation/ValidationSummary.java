package com.butchercraft.engine.validation;

import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationWarning;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable ordered summary of validation rule evaluation.
 *
 * <p>The first rejection, if any, stops later validation. Warnings from earlier accepted rules are
 * retained for operation results and diagnostics.</p>
 */
public record ValidationSummary(
        List<ValidationResult> results,
        Optional<FailureReason> rejectionReason,
        List<OperationWarning> warnings
) {
    public ValidationSummary {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        rejectionReason = Objects.requireNonNull(rejectionReason, "rejectionReason");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public boolean accepted() {
        return rejectionReason.isEmpty();
    }
}
