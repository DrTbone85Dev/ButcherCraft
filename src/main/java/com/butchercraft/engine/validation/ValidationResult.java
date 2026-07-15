package com.butchercraft.engine.validation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationWarning;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable inspectable result from one validation rule.
 *
 * <p>A result is accepted or rejected. Accepted results may carry one warning. Rejected results
 * always carry a stable failure reason. The type avoids null-based validation outcomes.</p>
 */
public record ValidationResult(
        ValidationStatus status,
        Optional<FailureReason> rejectionReason,
        Optional<OperationWarning> warning
) {
    public ValidationResult {
        Objects.requireNonNull(status, "status");
        rejectionReason = Objects.requireNonNull(rejectionReason, "rejectionReason");
        warning = Objects.requireNonNull(warning, "warning");
        if (status == ValidationStatus.ACCEPTED && rejectionReason.isPresent()) {
            throw new IllegalArgumentException("Accepted validation cannot contain a rejection reason");
        }
        if (status == ValidationStatus.REJECTED && rejectionReason.isEmpty()) {
            throw new IllegalArgumentException("Rejected validation requires a rejection reason");
        }
    }

    public static ValidationResult accepted() {
        return new ValidationResult(ValidationStatus.ACCEPTED, Optional.empty(), Optional.empty());
    }

    public static ValidationResult warning(EngineId sourceId, String message) {
        return new ValidationResult(
                ValidationStatus.ACCEPTED,
                Optional.empty(),
                Optional.of(new OperationWarning(sourceId, message))
        );
    }

    public static ValidationResult rejected(String code, String explanation) {
        return new ValidationResult(
                ValidationStatus.REJECTED,
                Optional.of(new FailureReason(code, explanation)),
                Optional.empty()
        );
    }

    public boolean isAccepted() {
        return status == ValidationStatus.ACCEPTED;
    }
}
