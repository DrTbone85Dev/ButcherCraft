package com.butchercraft.transformation.datapack;

import com.butchercraft.transformation.TransformationRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of assembling a transformation registry from datapack content.
 */
public record TransformationDatapackLoadResult(
        Optional<TransformationRegistry> registry,
        List<TransformationDatapackValidationError> errors
) {
    public TransformationDatapackLoadResult {
        registry = Objects.requireNonNull(registry, "registry");
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        if ((registry.isPresent() && !errors.isEmpty()) || (registry.isEmpty() && errors.isEmpty())) {
            throw new IllegalArgumentException("Datapack load result must contain either a registry or errors");
        }
    }

    public static TransformationDatapackLoadResult success(TransformationRegistry registry) {
        return new TransformationDatapackLoadResult(Optional.of(registry), List.of());
    }

    public static TransformationDatapackLoadResult failure(List<TransformationDatapackValidationError> errors) {
        return new TransformationDatapackLoadResult(Optional.empty(), errors);
    }

    public boolean succeeded() {
        return registry.isPresent();
    }

    public String describeErrors() {
        return errors.stream()
                .map(error -> error.source()
                        + " ["
                        + error.code()
                        + "] "
                        + error.transformationId().map(id -> id + ": ").orElse("")
                        + error.message())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("No transformation datapack errors");
    }
}
