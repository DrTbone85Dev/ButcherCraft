package com.butchercraft.transformation;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic acceptance or rejection of a transformation request.
 */
public record TransformationEvaluation(
        TransformationEvaluationCode code,
        String message,
        Optional<TransformationId> transformationId
) {
    public TransformationEvaluation {
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Transformation evaluation message cannot be blank");
        }
        transformationId = Objects.requireNonNull(transformationId, "transformationId");
        transformationId.ifPresent(id -> Objects.requireNonNull(id, "transformationId value"));
    }

    public TransformationEvaluation(TransformationEvaluationCode code, String message) {
        this(code, message, Optional.empty());
    }

    public static TransformationEvaluation accepted() {
        return new TransformationEvaluation(TransformationEvaluationCode.ACCEPTED, "Transformation accepted", Optional.empty());
    }

    public static TransformationEvaluation accepted(TransformationId transformationId) {
        return new TransformationEvaluation(
                TransformationEvaluationCode.ACCEPTED,
                "Transformation accepted",
                Optional.of(Objects.requireNonNull(transformationId, "transformationId"))
        );
    }

    public static TransformationEvaluation rejected(TransformationEvaluationCode code, String message) {
        if (code == TransformationEvaluationCode.ACCEPTED) {
            throw new IllegalArgumentException("Rejected transformation evaluation cannot use ACCEPTED");
        }
        return new TransformationEvaluation(code, message, Optional.empty());
    }

    public boolean acceptedResult() {
        return code == TransformationEvaluationCode.ACCEPTED;
    }

    public String reasonCode() {
        return code.code();
    }
}
