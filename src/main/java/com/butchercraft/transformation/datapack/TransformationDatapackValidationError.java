package com.butchercraft.transformation.datapack;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured validation error produced while loading transformation datapack content.
 */
public record TransformationDatapackValidationError(
        String source,
        Optional<String> transformationId,
        TransformationDatapackErrorCode code,
        String message
) {
    public TransformationDatapackValidationError {
        source = Objects.requireNonNull(source, "source").strip();
        transformationId = Objects.requireNonNull(transformationId, "transformationId")
                .map(String::strip);
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (source.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("Transformation datapack errors require source and message");
        }
        transformationId.ifPresent(id -> {
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Transformation id cannot be blank when present");
            }
        });
    }

    public static TransformationDatapackValidationError of(
            String source,
            String transformationId,
            TransformationDatapackErrorCode code,
            String message
    ) {
        return new TransformationDatapackValidationError(source, Optional.ofNullable(transformationId), code, message);
    }

    public static TransformationDatapackValidationError withoutId(
            String source,
            TransformationDatapackErrorCode code,
            String message
    ) {
        return new TransformationDatapackValidationError(source, Optional.empty(), code, message);
    }
}
