package com.butchercraft.transformation;

/**
 * Pure Java executor for previously accepted transformation evaluations.
 */
public final class TransformationExecutor {
    private TransformationExecutor() {
    }

    public static TransformationExecution execute(
            TransformationDefinition definition,
            TransformationContext context,
            TransformationEvaluation evaluation
    ) {
        if (definition == null || context == null || evaluation == null) {
            return TransformationExecution.rejected(
                    TransformationExecutionCode.INVALID_REQUEST,
                    "Transformation definition, context, and evaluation are required"
            );
        }
        if (!evaluation.acceptedResult()) {
            return TransformationExecution.rejected(
                    TransformationExecutionCode.EVALUATION_NOT_ACCEPTED,
                    "Transformation execution requires an accepted evaluation"
            );
        }
        if (evaluation.transformationId().isEmpty() || !definition.id().equals(evaluation.transformationId().orElseThrow())) {
            return TransformationExecution.rejected(
                    TransformationExecutionCode.EVALUATION_MISMATCH,
                    "Accepted evaluation does not belong to this transformation"
            );
        }
        TransformationEvaluation currentEvaluation = TransformationEvaluator.evaluate(definition, context);
        if (!currentEvaluation.equals(evaluation)) {
            return TransformationExecution.rejected(
                    TransformationExecutionCode.EVALUATION_MISMATCH,
                    "Accepted evaluation no longer matches the supplied transformation context"
            );
        }
        return TransformationExecution.executed(definition);
    }
}
