package com.butchercraft.transformation;

import java.util.List;
import java.util.Objects;

/**
 * Immutable side-effect-free execution result for a transformation output plan.
 */
public record TransformationExecution(
        TransformationExecutionCode code,
        String message,
        List<TransformationOutput> outputs
) {
    public TransformationExecution {
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Transformation execution message cannot be blank");
        }
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        if (code == TransformationExecutionCode.EXECUTED && outputs.isEmpty()) {
            throw new IllegalArgumentException("Successful transformation execution requires outputs");
        }
        if (code != TransformationExecutionCode.EXECUTED && !outputs.isEmpty()) {
            throw new IllegalArgumentException("Rejected transformation execution cannot contain outputs");
        }
    }

    public static TransformationExecution executed(TransformationDefinition definition) {
        return new TransformationExecution(
                TransformationExecutionCode.EXECUTED,
                "Transformation executed",
                Objects.requireNonNull(definition, "definition").outputs()
        );
    }

    public static TransformationExecution rejected(TransformationExecutionCode code, String message) {
        if (code == TransformationExecutionCode.EXECUTED) {
            throw new IllegalArgumentException("Rejected transformation execution cannot use EXECUTED");
        }
        return new TransformationExecution(code, message, List.of());
    }

    public boolean succeeded() {
        return code == TransformationExecutionCode.EXECUTED;
    }

    public String reasonCode() {
        return code.code();
    }
}
