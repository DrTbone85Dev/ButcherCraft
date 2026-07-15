package com.butchercraft.engine.context;

import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.product.Product;

import java.util.List;
import java.util.Objects;

/**
 * Immutable context supplied when an operation is evaluated.
 *
 * <p>The context contains only explicit processing facts: input product, requested operation,
 * cleanliness, operator skill, equipment condition, and additional modifiers. It has no world,
 * entity, inventory, callback, or Minecraft dependency. Future integration code gathers these
 * facts and passes them into the engine.</p>
 */
public record ProcessingContext(
        Product inputProduct,
        ProcessingOperation operation,
        ProcessingFactor cleanliness,
        ProcessingFactor operatorSkill,
        ProcessingFactor equipmentCondition,
        List<ProcessingModifier> additionalModifiers
) {
    public ProcessingContext {
        Objects.requireNonNull(inputProduct, "inputProduct");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(cleanliness, "cleanliness");
        Objects.requireNonNull(operatorSkill, "operatorSkill");
        Objects.requireNonNull(equipmentCondition, "equipmentCondition");
        additionalModifiers = List.copyOf(Objects.requireNonNull(additionalModifiers, "additionalModifiers"));
    }

    /**
     * Creates a context with neutral/default factors.
     *
     * <p>Cleanliness and equipment condition default to 1000. Operator skill defaults to 500.
     * No additional modifiers are supplied.</p>
     */
    public static ProcessingContext neutral(Product inputProduct, ProcessingOperation operation) {
        return new ProcessingContext(
                inputProduct,
                operation,
                ProcessingFactor.IDEAL,
                ProcessingFactor.NEUTRAL_SKILL,
                ProcessingFactor.IDEAL,
                List.of()
        );
    }
}
