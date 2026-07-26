package com.butchercraft.world.transaction;

import com.butchercraft.world.transaction.binding.AuthoritativeTransactionResultEvidence;
import com.butchercraft.world.transaction.binding.TransactionTerminalResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TransactionResult(
        boolean success,
        Optional<TransactionFailureCode> failureCode,
        List<String> validationMessages,
        List<TransactionAppliedChange> appliedChanges,
        long executionTick,
        Optional<AuthoritativeTransactionResultEvidence> resultEvidence,
        TransactionTerminalResult terminalResult
) {
    public TransactionResult {
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        validationMessages = copyMessages(validationMessages);
        appliedChanges = List.copyOf(Objects.requireNonNull(appliedChanges, "appliedChanges"));
        resultEvidence = Objects.requireNonNull(resultEvidence, "resultEvidence");
        terminalResult = Objects.requireNonNull(terminalResult, "terminalResult");
        appliedChanges.forEach(change -> Objects.requireNonNull(change, "appliedChange"));
        if (executionTick < 0L) {
            throw new IllegalArgumentException("Transaction execution tick must not be negative: " + executionTick);
        }
        if (success && failureCode.isPresent()) {
            throw new IllegalArgumentException("Successful transaction result cannot contain a failure code");
        }
        if (!success && failureCode.isEmpty()) {
            throw new IllegalArgumentException("Failed transaction result requires a failure code");
        }
        if (!success && !appliedChanges.isEmpty()) {
            throw new IllegalArgumentException("Failed transaction result cannot contain applied changes");
        }
        resultEvidence.ifPresent(evidence -> {
            if (!evidence.digestMatches()) {
                throw new IllegalArgumentException("Transaction result evidence digest does not match its content");
            }
        });
    }

    public static TransactionResult applied(List<TransactionAppliedChange> changes, long executionTick) {
        return applied(changes, executionTick, Optional.empty());
    }

    public static TransactionResult applied(
            List<TransactionAppliedChange> changes,
            long executionTick,
            AuthoritativeTransactionResultEvidence resultEvidence
    ) {
        return applied(changes, executionTick, Optional.of(resultEvidence));
    }

    private static TransactionResult applied(
            List<TransactionAppliedChange> changes,
            long executionTick,
            Optional<AuthoritativeTransactionResultEvidence> resultEvidence
    ) {
        return new TransactionResult(
                true,
                Optional.empty(),
                List.of(),
                changes,
                executionTick,
                resultEvidence,
                TransactionTerminalResult.APPLIED
        );
    }

    public static TransactionResult rejected(
            TransactionFailureCode code,
            List<String> messages,
            long executionTick
    ) {
        return rejected(code, messages, executionTick, Optional.empty(), TransactionTerminalResult.REJECTED);
    }

    public static TransactionResult rejected(
            TransactionFailureCode code,
            List<String> messages,
            long executionTick,
            AuthoritativeTransactionResultEvidence resultEvidence
    ) {
        return rejected(code, messages, executionTick, Optional.of(resultEvidence), TransactionTerminalResult.REJECTED);
    }

    public static TransactionResult duplicateObservation(
            TransactionResult existing,
            long executionTick
    ) {
        Objects.requireNonNull(existing, "existing");
        return new TransactionResult(
                existing.success(),
                existing.failureCode(),
                existing.validationMessages().isEmpty()
                        ? List.of("Duplicate Transaction observation returned the existing authoritative result")
                        : existing.validationMessages(),
                existing.appliedChanges(),
                executionTick,
                existing.resultEvidence(),
                TransactionTerminalResult.DUPLICATE_OBSERVATION
        );
    }

    public static TransactionResult conflict(
            TransactionFailureCode code,
            List<String> messages,
            long executionTick,
            Optional<AuthoritativeTransactionResultEvidence> existingEvidence
    ) {
        return rejected(code, messages, executionTick, existingEvidence, TransactionTerminalResult.CONFLICT);
    }

    private static TransactionResult rejected(
            TransactionFailureCode code,
            List<String> messages,
            long executionTick,
            Optional<AuthoritativeTransactionResultEvidence> resultEvidence,
            TransactionTerminalResult terminalResult
    ) {
        return new TransactionResult(
                false,
                Optional.of(code),
                messages,
                List.of(),
                executionTick,
                resultEvidence,
                terminalResult
        );
    }

    private static List<String> copyMessages(List<String> messages) {
        return Objects.requireNonNull(messages, "validationMessages").stream()
                .map(message -> {
                    String normalized = Objects.requireNonNull(message, "validationMessage").strip();
                    if (normalized.isEmpty()) {
                        throw new IllegalArgumentException("Transaction validation message cannot be blank");
                    }
                    return normalized;
                })
                .toList();
    }
}
