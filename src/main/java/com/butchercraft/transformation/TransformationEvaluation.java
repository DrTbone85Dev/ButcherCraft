package com.butchercraft.transformation;

import java.util.Objects;

/**
 * Deterministic acceptance or rejection of a transformation request.
 */
public record TransformationEvaluation(
        TransformationEvaluationCode code,
        String message
) {
    public TransformationEvaluation {
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Transformation evaluation message cannot be blank");
        }
    }

    public static TransformationEvaluation accepted() {
        return new TransformationEvaluation(TransformationEvaluationCode.ACCEPTED, "Transformation accepted");
    }

    public static TransformationEvaluation rejected(TransformationEvaluationCode code, String message) {
        if (code == TransformationEvaluationCode.ACCEPTED) {
            throw new IllegalArgumentException("Rejected transformation evaluation cannot use ACCEPTED");
        }
        return new TransformationEvaluation(code, message);
    }

    public boolean acceptedResult() {
        return code == TransformationEvaluationCode.ACCEPTED;
    }

    public String reasonCode() {
        return code.code();
    }
}
