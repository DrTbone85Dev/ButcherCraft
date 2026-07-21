package com.butchercraft.product.datapack;

import com.butchercraft.product.definition.ProductRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of assembling a product registry from datapack content.
 */
public record ProductDatapackLoadResult(
        Optional<ProductRegistry> registry,
        List<ProductDatapackValidationError> errors
) {
    public ProductDatapackLoadResult {
        registry = Objects.requireNonNull(registry, "registry");
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        if ((registry.isPresent() && !errors.isEmpty()) || (registry.isEmpty() && errors.isEmpty())) {
            throw new IllegalArgumentException("Datapack load result must contain either a registry or errors");
        }
    }

    public static ProductDatapackLoadResult success(ProductRegistry registry) {
        return new ProductDatapackLoadResult(Optional.of(registry), List.of());
    }

    public static ProductDatapackLoadResult failure(List<ProductDatapackValidationError> errors) {
        return new ProductDatapackLoadResult(Optional.empty(), errors);
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
                        + error.productId().map(id -> id + ": ").orElse("")
                        + error.message())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("No product datapack errors");
    }
}
