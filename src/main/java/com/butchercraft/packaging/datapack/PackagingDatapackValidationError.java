package com.butchercraft.packaging.datapack;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured validation error produced while loading packaging datapack content.
 */
public record PackagingDatapackValidationError(
        String source,
        Optional<String> packagingId,
        PackagingDatapackErrorCode code,
        String message
) {
    public PackagingDatapackValidationError {
        source = Objects.requireNonNull(source, "source").strip();
        packagingId = Objects.requireNonNull(packagingId, "packagingId")
                .map(String::strip);
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (source.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("Packaging datapack errors require source and message");
        }
        packagingId.ifPresent(id -> {
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Packaging id cannot be blank when present");
            }
        });
    }

    public static PackagingDatapackValidationError of(
            String source,
            String packagingId,
            PackagingDatapackErrorCode code,
            String message
    ) {
        return new PackagingDatapackValidationError(source, Optional.ofNullable(packagingId), code, message);
    }

    public static PackagingDatapackValidationError withoutId(
            String source,
            PackagingDatapackErrorCode code,
            String message
    ) {
        return new PackagingDatapackValidationError(source, Optional.empty(), code, message);
    }
}
