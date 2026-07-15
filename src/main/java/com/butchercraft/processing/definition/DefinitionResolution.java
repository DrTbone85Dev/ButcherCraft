package com.butchercraft.processing.definition;

import java.util.Objects;
import java.util.Optional;

public record DefinitionResolution<T>(Optional<T> value, DefinitionValidationReport report) {
    public DefinitionResolution {
        value = Objects.requireNonNull(value, "value");
        report = Objects.requireNonNull(report, "report");
        if (value.isPresent() && report.hasErrors()) {
            throw new IllegalArgumentException("Successful definition resolution cannot contain errors");
        }
    }

    public static <T> DefinitionResolution<T> success(T value, DefinitionValidationReport report) {
        return new DefinitionResolution<>(Optional.of(Objects.requireNonNull(value, "value")), report);
    }

    public static <T> DefinitionResolution<T> success(T value) {
        return success(value, DefinitionValidationReport.EMPTY);
    }

    public static <T> DefinitionResolution<T> failure(DefinitionValidationReport report) {
        if (!Objects.requireNonNull(report, "report").hasErrors()) {
            throw new IllegalArgumentException("Failed definition resolution requires at least one error");
        }
        return new DefinitionResolution<>(Optional.empty(), report);
    }

    public boolean succeeded() {
        return value.isPresent();
    }

    public T orThrow() {
        return value.orElseThrow(() -> new IllegalStateException(report.issues().toString()));
    }
}
