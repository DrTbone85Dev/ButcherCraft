package com.butchercraft.world.production;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProductionOperationResult<T>(
        boolean accepted,
        Optional<T> value,
        List<ProductionFailure> failures
) {
    public ProductionOperationResult {
        value = Objects.requireNonNull(value, "value");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        failures.forEach(failure -> Objects.requireNonNull(failure, "failure"));
        if (accepted != failures.isEmpty() || accepted != value.isPresent()) {
            throw new IllegalArgumentException("Production operation result shape is inconsistent");
        }
    }

    public static <T> ProductionOperationResult<T> accepted(T value) {
        return new ProductionOperationResult<>(true, Optional.of(Objects.requireNonNull(value, "value")), List.of());
    }

    public static <T> ProductionOperationResult<T> rejected(ProductionFailure failure) {
        return new ProductionOperationResult<>(false, Optional.empty(), List.of(failure));
    }

    public static <T> ProductionOperationResult<T> rejected(List<ProductionFailure> failures) {
        if (Objects.requireNonNull(failures, "failures").isEmpty()) {
            throw new IllegalArgumentException("Rejected production operation requires a failure");
        }
        return new ProductionOperationResult<>(false, Optional.empty(), failures);
    }
}
