package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;

import java.util.Objects;

/**
 * Deterministic validation issue for a transformation product reference.
 */
public record TransformationProductReferenceIssue(
        TransformationId transformationId,
        EngineId productId,
        TransformationProductReferenceRole role,
        String code,
        String message
) {
    public TransformationProductReferenceIssue {
        Objects.requireNonNull(transformationId, "transformationId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(role, "role");
        code = Objects.requireNonNull(code, "code").strip();
        message = Objects.requireNonNull(message, "message").strip();
        if (code.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("Product reference issue code and message must be present");
        }
    }
}
