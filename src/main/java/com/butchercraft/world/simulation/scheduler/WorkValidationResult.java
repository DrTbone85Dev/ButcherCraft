package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record WorkValidationResult(boolean accepted, Optional<WorkFailureCode> failureCode, List<String> messages) {
    public WorkValidationResult {
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (accepted == failureCode.isPresent()) throw new IllegalArgumentException("Validation outcome is inconsistent");
    }
    public static WorkValidationResult acceptedResult() {
        return new WorkValidationResult(true, Optional.empty(), List.of());
    }
    public static WorkValidationResult rejected(WorkFailureCode code, String message) {
        return new WorkValidationResult(false, Optional.of(code), List.of(message));
    }
}
