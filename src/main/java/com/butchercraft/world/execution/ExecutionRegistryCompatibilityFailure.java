package com.butchercraft.world.execution;

import java.util.Objects;
import java.util.Optional;

public record ExecutionRegistryCompatibilityFailure(
        ExecutionRegistryCompatibilityFailureCode code,
        String message,
        Optional<String> operationIdentity,
        Optional<String> handlerId
) {
    public ExecutionRegistryCompatibilityFailure {
        code = Objects.requireNonNull(code, "code");
        message = ExecutionValidation.requireText(
                message,
                "Execution registry compatibility failure message",
                512
        );
        operationIdentity = Objects.requireNonNull(operationIdentity, "operationIdentity")
                .map(value -> ExecutionValidation.requireId(value, "Execution operation identity"));
        handlerId = Objects.requireNonNull(handlerId, "handlerId")
                .map(value -> ExecutionValidation.requireId(value, "Execution handler id"));
    }

    public static ExecutionRegistryCompatibilityFailure registry(
            ExecutionRegistryCompatibilityFailureCode code,
            String message
    ) {
        return new ExecutionRegistryCompatibilityFailure(code, message, Optional.empty(), Optional.empty());
    }

    public static ExecutionRegistryCompatibilityFailure handler(
            ExecutionRegistryCompatibilityFailureCode code,
            String message,
            String handlerId
    ) {
        return new ExecutionRegistryCompatibilityFailure(code, message, Optional.empty(), Optional.of(handlerId));
    }

    public static ExecutionRegistryCompatibilityFailure operation(
            ExecutionRegistryCompatibilityFailureCode code,
            String message,
            String operationIdentity,
            String handlerId
    ) {
        return new ExecutionRegistryCompatibilityFailure(
                code,
                message,
                Optional.of(operationIdentity),
                Optional.of(handlerId)
        );
    }
}
