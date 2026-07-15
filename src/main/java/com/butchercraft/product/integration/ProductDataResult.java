package com.butchercraft.product.integration;

import com.butchercraft.engine.result.FailureReason;

import java.util.Objects;
import java.util.Optional;

/**
 * Small explicit result wrapper for product data integration.
 *
 * <p>Adapters return this instead of null or boolean-only outcomes so missing or invalid stack
 * data remains inspectable.</p>
 */
public record ProductDataResult<T>(Optional<T> value, Optional<FailureReason> failureReason) {
    public ProductDataResult {
        value = Objects.requireNonNull(value, "value");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        if (value.isPresent() == failureReason.isPresent()) {
            throw new IllegalArgumentException("Product data result must contain exactly one value or failure reason");
        }
    }

    public static <T> ProductDataResult<T> success(T value) {
        return new ProductDataResult<>(Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
    }

    public static <T> ProductDataResult<T> failure(String code, String message) {
        return new ProductDataResult<>(Optional.empty(), Optional.of(new FailureReason(code, message)));
    }

    public boolean succeeded() {
        return value.isPresent();
    }

    public T orThrow() {
        return value.orElseThrow(() -> new IllegalStateException(failureReason.orElseThrow().message()));
    }
}
