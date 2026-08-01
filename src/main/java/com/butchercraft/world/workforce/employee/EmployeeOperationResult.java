package com.butchercraft.world.workforce.employee;

import java.util.Objects;
import java.util.Optional;

public record EmployeeOperationResult<T>(Optional<T> value, Optional<EmployeeFailure> failure) {
    public EmployeeOperationResult {
        value = Objects.requireNonNull(value, "value");
        failure = Objects.requireNonNull(failure, "failure");
        if (value.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Employee result must contain exactly one value or failure");
        }
    }

    public static <T> EmployeeOperationResult<T> succeeded(T value) {
        return new EmployeeOperationResult<>(Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
    }

    public static <T> EmployeeOperationResult<T> failed(EmployeeFailureCode code, String detail) {
        return new EmployeeOperationResult<>(Optional.empty(), Optional.of(new EmployeeFailure(code, detail)));
    }

    public boolean succeeded() {
        return value.isPresent();
    }

    public T orThrow() {
        return value.orElseThrow(() -> new IllegalStateException(failure.orElseThrow().detail()));
    }
}
