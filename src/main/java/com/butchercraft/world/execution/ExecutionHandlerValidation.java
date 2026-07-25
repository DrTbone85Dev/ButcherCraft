package com.butchercraft.world.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ExecutionHandlerValidation(
        boolean accepted,
        Optional<ExecutionFailureCode> failureCode,
        List<String> messages
) {
    public ExecutionHandlerValidation {
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = Objects.requireNonNull(messages, "messages").stream()
                .map(message -> ExecutionValidation.requireText(message, "Handler validation message", 2_048))
                .toList();
        if (accepted && failureCode.isPresent()) {
            throw new IllegalArgumentException("Accepted handler validation cannot contain a failure code");
        }
        if (!accepted && failureCode.isEmpty()) {
            throw new IllegalArgumentException("Rejected handler validation requires a failure code");
        }
    }

    public static ExecutionHandlerValidation acceptedResult() {
        return new ExecutionHandlerValidation(true, Optional.empty(), List.of());
    }

    public static ExecutionHandlerValidation rejected(ExecutionFailureCode code, String message) {
        return new ExecutionHandlerValidation(false, Optional.of(code), List.of(message));
    }
}
