package com.butchercraft.packaging.datapack;

import com.butchercraft.packaging.definition.PackagingRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of assembling a packaging registry from datapack content.
 */
public record PackagingDatapackLoadResult(
        Optional<PackagingRegistry> registry,
        List<PackagingDatapackValidationError> errors
) {
    public PackagingDatapackLoadResult {
        registry = Objects.requireNonNull(registry, "registry");
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        if ((registry.isPresent() && !errors.isEmpty()) || (registry.isEmpty() && errors.isEmpty())) {
            throw new IllegalArgumentException("Datapack load result must contain either a registry or errors");
        }
    }

    public static PackagingDatapackLoadResult success(PackagingRegistry registry) {
        return new PackagingDatapackLoadResult(Optional.of(registry), List.of());
    }

    public static PackagingDatapackLoadResult failure(List<PackagingDatapackValidationError> errors) {
        return new PackagingDatapackLoadResult(Optional.empty(), errors);
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
                        + error.packagingId().map(id -> id + ": ").orElse("")
                        + error.message())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("No packaging datapack errors");
    }
}
