package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SchedulerOperationResult(
        boolean successful,
        Optional<WorkFailureCode> failureCode,
        List<String> messages
) {
    public SchedulerOperationResult {
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (successful == failureCode.isPresent()) {
            throw new IllegalArgumentException("Scheduler operation result is inconsistent");
        }
    }

    public static SchedulerOperationResult success() {
        return new SchedulerOperationResult(true, Optional.empty(), List.of());
    }

    public static SchedulerOperationResult failure(WorkFailureCode code, String message) {
        return new SchedulerOperationResult(false, Optional.of(code), List.of(message));
    }
}
