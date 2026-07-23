package com.butchercraft.world.production;

import java.util.Objects;
import java.util.Optional;

public record ProductionFailure(
        ProductionFailureCode code,
        String message,
        Optional<String> reference
) {
    public ProductionFailure {
        code = Objects.requireNonNull(code, "code");
        message = ProductionValidation.requireText(message, "Production failure message", 2_048);
        reference = Objects.requireNonNull(reference, "reference")
                .map(value -> ProductionValidation.requireId(value, "Production failure reference"));
    }

    public static ProductionFailure of(ProductionFailureCode code, String message) {
        return new ProductionFailure(code, message, Optional.empty());
    }

    public static ProductionFailure of(ProductionFailureCode code, String message, String reference) {
        return new ProductionFailure(code, message, Optional.of(reference));
    }
}
