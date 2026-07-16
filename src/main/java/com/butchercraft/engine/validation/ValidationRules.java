package com.butchercraft.engine.validation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.modifier.ModifierApplication;
import com.butchercraft.engine.modifier.ModifierSystem;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Factory for the small reusable validation rules needed by the processing framework.
 *
 * <p>This is not a large rule engine. The returned rules are immutable, deterministic, and
 * Minecraft-independent.</p>
 */
public final class ValidationRules {
    private ValidationRules() {
    }

    public static ValidationRule requiredProductType() {
        return new SimpleRule(EngineId.of("validation/required_product_type"), context -> {
            if (!context.inputProduct().typeId().equals(context.operation().requiredProductType())) {
                return ValidationResult.rejected("wrong_product_type", "Input product type does not match operation requirement");
            }
            return ValidationResult.accepted();
        });
    }

    public static ValidationRule requiredSourceCategory() {
        return new SimpleRule(EngineId.of("validation/required_source_category"), context -> {
            ProductCategory actual = context.inputProduct().sourceCategory();
            if (context.operation().requiredSourceCategory().isPresent()
                    && !actual.equals(context.operation().requiredSourceCategory().orElseThrow())) {
                return ValidationResult.rejected("wrong_source_category", "Input source category does not match operation requirement");
            }
            return ValidationResult.accepted();
        });
    }

    public static ValidationRule requiredProcessingState() {
        return new SimpleRule(EngineId.of("validation/required_processing_state"), context -> {
            if (!context.inputProduct().processingState().equals(context.operation().requiredProcessingState())) {
                return ValidationResult.rejected("wrong_processing_state", "Input processing state does not match operation requirement");
            }
            return ValidationResult.accepted();
        });
    }

    public static ValidationRule minimumQuantity(ProductQuantity minimumQuantity) {
        Objects.requireNonNull(minimumQuantity, "minimumQuantity");
        return new SimpleRule(EngineId.of("validation/minimum_quantity"), context -> {
            ProductQuantity actual = context.inputProduct().quantity();
            if (actual.unit() != minimumQuantity.unit()) {
                return ValidationResult.rejected("minimum_quantity_unit_mismatch", "Input quantity unit does not match minimum quantity unit");
            }
            if (actual.amount() < minimumQuantity.amount()) {
                return ValidationResult.rejected("minimum_quantity_not_met", "Input quantity is below the operation minimum");
            }
            return ValidationResult.accepted();
        });
    }

    public static ValidationRule minimumCleanliness(ProcessingFactor minimumCleanliness) {
        Objects.requireNonNull(minimumCleanliness, "minimumCleanliness");
        return new SimpleRule(EngineId.of("validation/minimum_cleanliness"), context -> {
            if (context.cleanliness().value() < minimumCleanliness.value()) {
                return ValidationResult.rejected("minimum_cleanliness_not_met", "Cleanliness is below the operation minimum");
            }
            return ValidationResult.accepted();
        });
    }

    public static ValidationRule minimumEquipmentCondition(ProcessingFactor minimumCondition) {
        Objects.requireNonNull(minimumCondition, "minimumCondition");
        return new SimpleRule(EngineId.of("validation/minimum_equipment_condition"), context -> {
            if (context.equipmentCondition().value() < minimumCondition.value()) {
                return ValidationResult.rejected("minimum_equipment_condition_not_met", "Equipment condition is below the operation minimum");
            }
            return ValidationResult.accepted();
        });
    }

    public static ValidationRule zeroOutputNotPermitted() {
        return new SimpleRule(EngineId.of("validation/zero_output_not_permitted"), context -> {
            if (context.operation().zeroOutputPermitted()) {
                return ValidationResult.accepted();
            }
            List<ProcessingModifier> explicitModifiers = new ArrayList<>();
            explicitModifiers.addAll(context.operation().modifiers());
            explicitModifiers.addAll(context.additionalModifiers());
            ModifierApplication application = ModifierSystem.apply(explicitModifiers);
            ProductQuantity quantity = context.operation().totalOutputQuantity(
                    context.inputProduct().quantity(),
                    application.yieldBasisPointsDelta()
            );
            if (quantity.isZero()) {
                return ValidationResult.rejected("zero_output_not_permitted", "Operation would produce zero output");
            }
            return ValidationResult.accepted();
        });
    }

    public static ValidationRule warning(EngineId id, String message) {
        Objects.requireNonNull(id, "id");
        String checkedMessage = Objects.requireNonNull(message, "message").strip();
        if (checkedMessage.isEmpty()) {
            throw new IllegalArgumentException("Validation warning message cannot be blank");
        }
        return new SimpleRule(id, context -> ValidationResult.warning(id, checkedMessage));
    }

    private record SimpleRule(EngineId id, RuleEvaluation evaluation) implements ValidationRule {
        private SimpleRule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(evaluation, "evaluation");
        }

        @Override
        public ValidationResult evaluate(ProcessingContext context) {
            return evaluation.evaluate(Objects.requireNonNull(context, "context"));
        }
    }

    @FunctionalInterface
    private interface RuleEvaluation {
        ValidationResult evaluate(ProcessingContext context);
    }
}
