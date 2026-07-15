package com.butchercraft.engine.operation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.validation.ValidationRule;
import com.butchercraft.engine.validation.ValidationRules;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable product transformation definition used by processing transactions.
 *
 * <p>This is not a recipe registry, datapack format, machine definition, or expansion API. It
 * describes accepted inputs, output shape, exact duration, yield, quality adjustment, validation
 * rules, and static modifiers. It owns no transaction state or mutable work progress. Minecraft
 * integration may later build these records from recipes or data.</p>
 */
public record ProcessingOperation(
        EngineId id,
        String name,
        EngineId requiredProductType,
        Optional<ProductCategory> requiredSourceCategory,
        ProcessingState requiredProcessingState,
        EngineId outputProductType,
        ProcessingState outputProcessingState,
        ProcessingDuration baseDuration,
        YieldRatio baseYield,
        int baseQualityDelta,
        List<ValidationRule> validationRules,
        List<ProcessingModifier> modifiers,
        boolean zeroOutputPermitted
) {
    public ProcessingOperation {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Operation name cannot be blank");
        }
        Objects.requireNonNull(requiredProductType, "requiredProductType");
        requiredSourceCategory = Objects.requireNonNull(requiredSourceCategory, "requiredSourceCategory");
        requiredSourceCategory.ifPresent(category -> Objects.requireNonNull(category, "requiredSourceCategory value"));
        Objects.requireNonNull(requiredProcessingState, "requiredProcessingState");
        Objects.requireNonNull(outputProductType, "outputProductType");
        Objects.requireNonNull(outputProcessingState, "outputProcessingState");
        Objects.requireNonNull(baseDuration, "baseDuration");
        Objects.requireNonNull(baseYield, "baseYield");
        validationRules = List.copyOf(Objects.requireNonNull(validationRules, "validationRules"));
        modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
    }

    public ProcessingOperation(
            EngineId id,
            EngineId requiredProductType,
            ProcessingState requiredProcessingState,
            EngineId outputProductType,
            ProcessingState outputProcessingState,
            long baseDurationMilliseconds,
            YieldRatio baseYield,
            int baseQualityDelta,
            List<ProcessingModifier> modifiers
    ) {
        this(
                id,
                id.value(),
                requiredProductType,
                Optional.empty(),
                requiredProcessingState,
                outputProductType,
                outputProcessingState,
                ProcessingDuration.milliseconds(baseDurationMilliseconds),
                baseYield,
                baseQualityDelta,
                List.of(
                        ValidationRules.requiredProductType(),
                        ValidationRules.requiredProcessingState(),
                        ValidationRules.zeroOutputNotPermitted()
                ),
                modifiers,
                false
        );
    }
}
