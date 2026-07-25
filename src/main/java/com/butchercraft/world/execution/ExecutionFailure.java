package com.butchercraft.world.execution;

import java.util.Objects;

public record ExecutionFailure(
        int schemaVersion,
        ExecutionFailureCode code,
        String message,
        String referenceIdentity
) {
    public ExecutionFailure {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution failure");
        code = Objects.requireNonNull(code, "code");
        message = ExecutionValidation.requireText(message, "Execution failure message", 2_048);
        referenceIdentity = ExecutionValidation.requireId(referenceIdentity, "Execution failure reference");
    }

    public static ExecutionFailure of(ExecutionFailureCode code, String message, String referenceIdentity) {
        return new ExecutionFailure(ExecutionSchema.CURRENT_VERSION, code, message, referenceIdentity);
    }
}
