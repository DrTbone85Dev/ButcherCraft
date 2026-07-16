package com.butchercraft.engine.evaluation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.modifier.ModifierApplication;
import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ModifierSystem;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.ProcessingOutputDefinition;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationOutputResult;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.result.OperationWarning;
import com.butchercraft.engine.transaction.TransactionState;
import com.butchercraft.engine.validation.ValidationResult;
import com.butchercraft.engine.validation.ValidationRule;
import com.butchercraft.engine.validation.ValidationSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Focused deterministic evaluator for processing preparation.
 *
 * <p>The evaluator validates a context, gathers explicit operation/context modifiers, derives
 * inspectable quality modifiers from bounded context factors, calculates proposed output, and
 * returns an {@link OperationResult}. It does not commit, mutate inventories, or own transaction
 * state. Minecraft integration remains outside this package.</p>
 */
public final class ProcessingEvaluator {
    private static final EngineId CLEANLINESS_MODIFIER_ID = EngineId.of("context/cleanliness_factor");
    private static final EngineId EQUIPMENT_MODIFIER_ID = EngineId.of("context/equipment_condition");
    private static final EngineId OPERATOR_SKILL_MODIFIER_ID = EngineId.of("context/operator_skill");

    private ProcessingEvaluator() {
    }

    /**
     * Evaluates validation rules in operation order and stops at the first rejection.
     *
     * @param operation operation that must match the context operation
     * @param context immutable processing context
     * @return ordered validation summary
     */
    public static ValidationSummary validate(ProcessingOperation operation, ProcessingContext context) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(context, "context");
        List<ValidationResult> results = new ArrayList<>();
        List<OperationWarning> warnings = new ArrayList<>();

        if (!context.operation().id().equals(operation.id())) {
            FailureReason reason = new FailureReason("context_operation_mismatch", "Processing context references a different operation");
            return new ValidationSummary(results, Optional.of(reason), warnings);
        }

        for (ValidationRule rule : operation.validationRules()) {
            ValidationResult result;
            try {
                result = rule.evaluate(context);
            } catch (RuntimeException exception) {
                FailureReason reason = new FailureReason("validation_rule_failed", exception.getMessage());
                return new ValidationSummary(results, Optional.of(reason), warnings);
            }
            results.add(result);
            result.warning().ifPresent(warnings::add);
            if (!result.isAccepted()) {
                return new ValidationSummary(results, result.rejectionReason(), warnings);
            }
        }

        return new ValidationSummary(results, Optional.empty(), warnings);
    }

    /**
     * Prepares a proposed output without committing it.
     *
     * @param operation operation that must match the context operation
     * @param context immutable processing context
     * @return prepared success, validation rejection, or explicit failure
     */
    public static OperationResult prepare(ProcessingOperation operation, ProcessingContext context) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(context, "context");
        ValidationSummary validation = validate(operation, context);
        if (!validation.accepted()) {
            return OperationResult.failure(
                    context.inputProduct(),
                    TransactionState.REJECTED,
                    validation.rejectionReason().orElseThrow(),
                    Optional.empty(),
                    List.of(),
                    validation.warnings()
            );
        }

        try {
            ModifierApplication application = ModifierSystem.apply(collectModifiers(context));
            List<OperationWarning> warnings = new ArrayList<>(validation.warnings());
            warnings.addAll(application.appliedModifiers().stream()
                    .filter(modifier -> modifier.category() == ModifierCategory.WARNING)
                    .map(modifier -> new OperationWarning(modifier.id(), modifier.reason()))
                    .toList());

            List<ProductQuantity> outputQuantities = operation.outputQuantities(
                    context.inputProduct().quantity(),
                    application.yieldBasisPointsDelta()
            );
            ProductQuantity totalQuantity = sum(outputQuantities);
            if (totalQuantity.isZero() && !operation.zeroOutputPermitted()) {
                return OperationResult.failure(
                        context.inputProduct(),
                        TransactionState.REJECTED,
                        new FailureReason("zero_output_not_permitted", "Operation would produce zero output"),
                        Optional.empty(),
                        application.appliedModifiers(),
                        warnings
                );
            }

            List<OperationOutputResult> proposedOutputs = new ArrayList<>();
            List<ProcessingOutputDefinition> outputDefinitions = operation.outputs();
            for (int index = 0; index < outputDefinitions.size(); index++) {
                ProcessingOutputDefinition outputDefinition = outputDefinitions.get(index);
                ProductQuantity outputQuantity = outputQuantities.get(index);
                if (outputQuantity.isZero() && !outputDefinition.zeroOutputPermitted()) {
                    return OperationResult.failure(
                            context.inputProduct(),
                            TransactionState.REJECTED,
                            new FailureReason("zero_output_not_permitted", "Operation would produce zero output"),
                            Optional.empty(),
                            application.appliedModifiers(),
                            warnings
                    );
                }

                int qualityDelta = Math.addExact(outputDefinition.qualityDelta(), application.qualityDelta());
                ProductQuality resultingQuality = context.inputProduct().quality().adjustedByClamped(qualityDelta);
                Product proposedOutput = new Product(
                        outputDefinition.productType(),
                        context.inputProduct().sourceCategory(),
                        outputDefinition.processingState(),
                        outputQuantity,
                        resultingQuality
                );
                proposedOutputs.add(new OperationOutputResult(index, proposedOutput));
            }

            return OperationResult.successOutputs(
                    context.inputProduct(),
                    TransactionState.PREPARED,
                    proposedOutputs,
                    List.of(),
                    application.appliedModifiers(),
                    warnings
            );
        } catch (RuntimeException exception) {
            return OperationResult.failure(
                    context.inputProduct(),
                    TransactionState.FAILED,
                    new FailureReason("preparation_failed", exception.getMessage()),
                    Optional.empty(),
                    List.of(),
                    validation.warnings()
            );
        }
    }

    private static ProductQuantity sum(List<ProductQuantity> quantities) {
        ProductQuantity total = new ProductQuantity(0, quantities.getFirst().unit());
        for (ProductQuantity quantity : quantities) {
            total = total.add(quantity);
        }
        return total;
    }

    private static List<ProcessingModifier> collectModifiers(ProcessingContext context) {
        List<ProcessingModifier> modifiers = new ArrayList<>();
        modifiers.addAll(context.operation().modifiers());
        modifiers.addAll(derivedContextModifiers(context));
        modifiers.addAll(context.additionalModifiers());
        return modifiers;
    }

    private static List<ProcessingModifier> derivedContextModifiers(ProcessingContext context) {
        List<ProcessingModifier> modifiers = new ArrayList<>();
        int cleanlinessEffect = -(1000 - context.cleanliness().value()) / 20;
        if (cleanlinessEffect != 0) {
            modifiers.add(new ProcessingModifier(
                    CLEANLINESS_MODIFIER_ID,
                    "Cleanliness factor " + context.cleanliness().value() + "/1000",
                    ModifierCategory.QUALITY,
                    cleanlinessEffect,
                    100
            ));
        }

        int equipmentEffect = -(1000 - context.equipmentCondition().value()) / 20;
        if (equipmentEffect != 0) {
            modifiers.add(new ProcessingModifier(
                    EQUIPMENT_MODIFIER_ID,
                    "Equipment condition " + context.equipmentCondition().value() + "/1000",
                    ModifierCategory.QUALITY,
                    equipmentEffect,
                    110
            ));
        }

        int skillEffect = (context.operatorSkill().value() - 500) / 25;
        if (skillEffect != 0) {
            modifiers.add(new ProcessingModifier(
                    OPERATOR_SKILL_MODIFIER_ID,
                    "Operator skill factor " + context.operatorSkill().value() + "/1000",
                    ModifierCategory.QUALITY,
                    skillEffect,
                    120
            ));
        }
        return modifiers;
    }
}
