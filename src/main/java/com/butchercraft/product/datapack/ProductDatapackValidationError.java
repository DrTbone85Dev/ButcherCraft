package com.butchercraft.product.datapack;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured validation error produced while loading product datapack content.
 */
public record ProductDatapackValidationError(
        String source,
        Optional<String> productId,
        ProductDatapackErrorCode code,
        String message
) {
    public ProductDatapackValidationError {
        source = Objects.requireNonNull(source, "source").strip();
        productId = Objects.requireNonNull(productId, "productId")
                .map(String::strip);
        Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").strip();
        if (source.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("Product datapack errors require source and message");
        }
        productId.ifPresent(id -> {
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Product id cannot be blank when present");
            }
        });
    }

    public static ProductDatapackValidationError of(
            String source,
            String productId,
            ProductDatapackErrorCode code,
            String message
    ) {
        return new ProductDatapackValidationError(source, Optional.ofNullable(productId), code, message);
    }

    public static ProductDatapackValidationError withoutId(
            String source,
            ProductDatapackErrorCode code,
            String message
    ) {
        return new ProductDatapackValidationError(source, Optional.empty(), code, message);
    }
}
