package com.butchercraft.world.transaction.binding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TransactionDuplicateDecision(
        TransactionDuplicateOutcome outcome,
        Optional<AuthoritativeTransactionResultEvidence> existingEvidence,
        List<TransactionBindingFailure> failures
) {
    public TransactionDuplicateDecision {
        outcome = Objects.requireNonNull(outcome, "outcome");
        existingEvidence = Objects.requireNonNull(existingEvidence, "existingEvidence");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
    }

    public boolean successful() {
        return failures.isEmpty();
    }

    public static TransactionDuplicateDecision newTransaction() {
        return new TransactionDuplicateDecision(TransactionDuplicateOutcome.NEW_TRANSACTION, Optional.empty(), List.of());
    }

    public static TransactionDuplicateDecision duplicateObservation(
            AuthoritativeTransactionResultEvidence existingEvidence
    ) {
        return new TransactionDuplicateDecision(
                TransactionDuplicateOutcome.DUPLICATE_OBSERVATION,
                Optional.of(existingEvidence),
                List.of()
        );
    }

    public static TransactionDuplicateDecision conflict(
            TransactionDuplicateOutcome outcome,
            AuthoritativeTransactionResultEvidence existingEvidence,
            TransactionBindingFailure failure
    ) {
        if (outcome == TransactionDuplicateOutcome.NEW_TRANSACTION
                || outcome == TransactionDuplicateOutcome.DUPLICATE_OBSERVATION) {
            throw new IllegalArgumentException("Conflict decision requires a conflict outcome: " + outcome);
        }
        return new TransactionDuplicateDecision(outcome, Optional.of(existingEvidence), List.of(failure));
    }
}
