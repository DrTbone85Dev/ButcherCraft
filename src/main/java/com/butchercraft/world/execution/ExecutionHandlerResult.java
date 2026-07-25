package com.butchercraft.world.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record ExecutionHandlerResult(
        ExecutionHandlerOutcome outcome,
        Optional<ExecutionOwnerResultEvidence> ownerResultEvidence,
        Optional<ExecutionFailure> failure,
        OptionalLong nextEligibleTick,
        List<String> diagnostics,
        int workUnits
) {
    public ExecutionHandlerResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
        ownerResultEvidence = Objects.requireNonNull(ownerResultEvidence, "ownerResultEvidence");
        ownerResultEvidence.ifPresent(evidence -> {
            if (!evidence.digestMatches()) {
                throw new IllegalArgumentException("Owner result evidence digest mismatch");
            }
        });
        failure = Objects.requireNonNull(failure, "failure");
        nextEligibleTick = Objects.requireNonNull(nextEligibleTick, "nextEligibleTick");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics").stream()
                .map(message -> ExecutionValidation.requireText(message, "Execution handler diagnostic", 2_048))
                .toList();
        if (workUnits < 0) {
            throw new IllegalArgumentException("Execution handler work units must not be negative");
        }
        switch (outcome) {
            case OWNER_RESULT_PUBLISHED -> {
                if (ownerResultEvidence.isEmpty() || failure.isPresent() || nextEligibleTick.isPresent()) {
                    throw new IllegalArgumentException("Owner-result outcome has inconsistent fields");
                }
            }
            case WAITING_FOR_OWNER_RESULT -> {
                if (ownerResultEvidence.isPresent() || failure.isPresent() || nextEligibleTick.isEmpty()) {
                    throw new IllegalArgumentException("Waiting outcome has inconsistent fields");
                }
            }
            case REJECTED, FAILED -> {
                if (ownerResultEvidence.isPresent() || failure.isEmpty() || nextEligibleTick.isPresent()) {
                    throw new IllegalArgumentException("Terminal failure outcome has inconsistent fields");
                }
            }
        }
    }

    public static ExecutionHandlerResult ownerResult(ExecutionOwnerResultEvidence evidence, int workUnits) {
        return new ExecutionHandlerResult(
                ExecutionHandlerOutcome.OWNER_RESULT_PUBLISHED,
                Optional.of(evidence),
                Optional.empty(),
                OptionalLong.empty(),
                List.of(),
                workUnits
        );
    }

    public static ExecutionHandlerResult waiting(long nextEligibleTick, String message, int workUnits) {
        return new ExecutionHandlerResult(
                ExecutionHandlerOutcome.WAITING_FOR_OWNER_RESULT,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(nextEligibleTick),
                List.of(message),
                workUnits
        );
    }

    public static ExecutionHandlerResult rejected(ExecutionFailure failure, int workUnits) {
        return new ExecutionHandlerResult(
                ExecutionHandlerOutcome.REJECTED,
                Optional.empty(),
                Optional.of(failure),
                OptionalLong.empty(),
                List.of(failure.message()),
                workUnits
        );
    }

    public static ExecutionHandlerResult failed(ExecutionFailure failure, int workUnits) {
        return new ExecutionHandlerResult(
                ExecutionHandlerOutcome.FAILED,
                Optional.empty(),
                Optional.of(failure),
                OptionalLong.empty(),
                List.of(failure.message()),
                workUnits
        );
    }
}
