package com.butchercraft.engine.operation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.quantity.QuantityUnit;

import java.util.Objects;

/**
 * Immutable definition for one ordered output of a processing operation.
 */
public record ProcessingOutputDefinition(
        EngineId productType,
        ProcessingState processingState,
        YieldRatio yield,
        int qualityDelta,
        QuantityUnit quantityUnit,
        boolean zeroOutputPermitted
) {
    public ProcessingOutputDefinition {
        Objects.requireNonNull(productType, "productType");
        Objects.requireNonNull(processingState, "processingState");
        Objects.requireNonNull(yield, "yield");
        Objects.requireNonNull(quantityUnit, "quantityUnit");
    }
}
