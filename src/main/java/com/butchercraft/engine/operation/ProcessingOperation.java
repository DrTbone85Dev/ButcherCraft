package com.butchercraft.engine.operation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.product.ProcessingState;

import java.util.List;
import java.util.Objects;

/**
 * Narrow immutable operation specification used by processing transactions.
 *
 * <p>This is not a recipe registry, datapack format, machine definition, or expansion API. It
 * only defines the input and output constraints needed to prove transaction-safe domain logic.
 * Minecraft integration may later build these records from recipes or data.</p>
 */
public record ProcessingOperation(
        EngineId id,
        EngineId requiredProductType,
        ProcessingState requiredProcessingState,
        EngineId outputProductType,
        ProcessingState outputProcessingState,
        long baseDurationTicks,
        YieldRatio baseYield,
        int baseQualityDelta,
        List<ProcessingModifier> modifiers
) {
    public ProcessingOperation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requiredProductType, "requiredProductType");
        Objects.requireNonNull(requiredProcessingState, "requiredProcessingState");
        Objects.requireNonNull(outputProductType, "outputProductType");
        Objects.requireNonNull(outputProcessingState, "outputProcessingState");
        if (baseDurationTicks < 0) {
            throw new IllegalArgumentException("Base duration cannot be negative");
        }
        Objects.requireNonNull(baseYield, "baseYield");
        modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
    }
}
