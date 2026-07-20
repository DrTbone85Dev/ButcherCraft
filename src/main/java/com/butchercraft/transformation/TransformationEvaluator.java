package com.butchercraft.transformation;

import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.Optional;

/**
 * Side-effect-free evaluator for deterministic material transformation requests.
 */
public final class TransformationEvaluator {
    private TransformationEvaluator() {
    }

    public static TransformationEvaluation evaluate(
            TransformationDefinition definition,
            TransformationContext context
    ) {
        if (definition == null) {
            return TransformationEvaluation.rejected(
                    TransformationEvaluationCode.INVALID_CONTEXT,
                    "Transformation definition is required"
            );
        }
        if (context == null) {
            return TransformationEvaluation.rejected(
                    TransformationEvaluationCode.INVALID_CONTEXT,
                    "Transformation context is required"
            );
        }

        Optional<TransformationEvaluation> capabilityRejection = validateCapability(definition, context);
        if (capabilityRejection.isPresent()) {
            return capabilityRejection.orElseThrow();
        }

        for (TransformationInput input : definition.inputs()) {
            TransformationEvaluation inputEvaluation = evaluateInput(input, context);
            if (!inputEvaluation.acceptedResult()) {
                return inputEvaluation;
            }
        }
        return TransformationEvaluation.accepted(definition.id());
    }

    private static Optional<TransformationEvaluation> validateCapability(
            TransformationDefinition definition,
            TransformationContext context
    ) {
        if (definition.workstationCapability().isEmpty()) {
            return Optional.empty();
        }
        if (context.workstationCapability().isEmpty()) {
            return Optional.of(TransformationEvaluation.rejected(
                    TransformationEvaluationCode.UNSUPPORTED_CAPABILITY,
                    "Transformation requires workstation capability " + definition.workstationCapability().orElseThrow().value()
            ));
        }
        if (!context.workstationCapability().orElseThrow().advertises(definition.workstationCapability().orElseThrow())) {
            return Optional.of(TransformationEvaluation.rejected(
                    TransformationEvaluationCode.UNSUPPORTED_CAPABILITY,
                    "Workstation " + context.workstationCapability().orElseThrow().id().value()
                            + " does not advertise capability " + definition.workstationCapability().orElseThrow().value()
            ));
        }
        return Optional.empty();
    }

    private static TransformationEvaluation evaluateInput(
            TransformationInput input,
            TransformationContext context
    ) {
        MaterialAmount required = input.requiredAmount();
        ProductQuantity available;
        try {
            Optional<ProductQuantity> availableQuantity = context.availableQuantity(required.materialId());
            if (availableQuantity.isEmpty()) {
                return TransformationEvaluation.rejected(
                        TransformationEvaluationCode.MISSING_INPUT,
                        "Missing required input material " + required.materialId().value()
                );
            }
            available = availableQuantity.orElseThrow();
        } catch (RuntimeException exception) {
            return TransformationEvaluation.rejected(
                    TransformationEvaluationCode.INVALID_CONTEXT,
                    "Available material context is invalid: " + exception.getMessage()
            );
        }

        if (available.unit() != required.quantity().unit()) {
            return TransformationEvaluation.rejected(
                    TransformationEvaluationCode.INVALID_CONTEXT,
                    "Available input unit does not match required unit for " + required.materialId().value()
            );
        }
        if (available.amount() < required.quantity().amount()) {
            return TransformationEvaluation.rejected(
                    TransformationEvaluationCode.INSUFFICIENT_INPUT,
                    "Insufficient input material " + required.materialId().value()
            );
        }
        return TransformationEvaluation.accepted();
    }
}
