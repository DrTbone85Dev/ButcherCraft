package com.butchercraft.world.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ExecutionOperationResult<T>(
        boolean accepted,
        Optional<T> value,
        Optional<ExecutionFailureCode> failureCode,
        List<String> messages
) {
    public ExecutionOperationResult {
        value = Objects.requireNonNull(value, "value");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = Objects.requireNonNull(messages, "messages").stream()
                .map(message -> ExecutionValidation.requireText(message, "Execution operation message", 2_048))
                .toList();
        if (accepted && (value.isEmpty() || failureCode.isPresent())) {
            throw new IllegalArgumentException("Accepted Execution result shape is inconsistent");
        }
        if (!accepted && failureCode.isEmpty()) {
            throw new IllegalArgumentException("Rejected Execution result requires a failure code");
        }
    }

    public static <T> ExecutionOperationResult<T> accepted(T value) {
        return new ExecutionOperationResult<>(true, Optional.of(Objects.requireNonNull(value, "value")),
                Optional.empty(), List.of());
    }

    public static <T> ExecutionOperationResult<T> rejected(ExecutionFailureCode code, String message) {
        return new ExecutionOperationResult<>(false, Optional.empty(), Optional.of(code), List.of(message));
    }
}
