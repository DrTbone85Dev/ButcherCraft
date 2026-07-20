package com.butchercraft.workstation;

import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.transaction.TransactionState;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.MaterialAmount;
import com.butchercraft.transformation.TransformationContext;
import com.butchercraft.transformation.TransformationDefinition;
import com.butchercraft.transformation.TransformationEvaluation;
import com.butchercraft.transformation.TransformationExecution;
import com.butchercraft.transformation.TransformationExecutor;
import com.butchercraft.transformation.TransformationEvaluator;
import com.butchercraft.transformation.TransformationId;
import com.butchercraft.transformation.TransformationOutput;
import com.butchercraft.transformation.TransformationRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class TransformationWorkstationExecutionStrategy implements WorkstationExecutionStrategy {
    static final TransformationWorkstationExecutionStrategy INSTANCE =
            new TransformationWorkstationExecutionStrategy(BuiltInTransformationRegistry.builtInRegistry());

    private final TransformationRegistry registry;

    TransformationWorkstationExecutionStrategy(TransformationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public OperationResult prepare(WorkstationCapability capability, ResolvedWorkstationOperation operation) {
        TransformationPlan plan = plan(capability, operation);
        if (plan.hasFailure()) {
            return transformationFailure(
                    operation,
                    TransactionState.REJECTED,
                    plan.failureReason().code(),
                    plan.failureReason().message()
            );
        }
        if (!plan.evaluation().acceptedResult()) {
            return transformationFailure(operation, TransactionState.REJECTED, plan.evaluation().reasonCode(), plan.evaluation().message());
        }
        return LegacyWorkstationExecutionStrategy.INSTANCE.prepare(capability, operation);
    }

    @Override
    public OperationResult commit(WorkstationCapability capability, ResolvedWorkstationOperation operation) {
        TransformationPlan plan = plan(capability, operation);
        if (plan.hasFailure()) {
            return transformationFailure(
                    operation,
                    TransactionState.REJECTED,
                    plan.failureReason().code(),
                    plan.failureReason().message()
            );
        }
        if (!plan.evaluation().acceptedResult()) {
            return transformationFailure(operation, TransactionState.REJECTED, plan.evaluation().reasonCode(), plan.evaluation().message());
        }
        TransformationExecution execution = TransformationExecutor.execute(plan.definition(), plan.context(), plan.evaluation());
        if (!execution.succeeded()) {
            return transformationFailure(operation, TransactionState.REJECTED, execution.reasonCode(), execution.message());
        }

        OperationResult committed = LegacyWorkstationExecutionStrategy.INSTANCE.commit(capability, operation);
        if (!committed.succeeded()) {
            return committed;
        }
        Optional<String> mismatch = firstOutputMismatch(execution.outputs(), committed.committedOutputs());
        if (mismatch.isPresent()) {
            return transformationFailure(operation, TransactionState.FAILED, "transformation_output_mismatch", mismatch.orElseThrow());
        }
        return committed;
    }

    private TransformationPlan plan(WorkstationCapability capability, ResolvedWorkstationOperation operation) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(operation, "operation");
        TransformationId transformationId = new TransformationId(operation.engineOperation().id());
        Optional<TransformationDefinition> registeredDefinition = registry.find(transformationId);
        if (registeredDefinition.isEmpty()) {
            return TransformationPlan.rejected(
                    "missing_transformation_definition",
                    "No registered transformation definition exists for " + transformationId.value()
            );
        }

        TransformationDefinition definition;
        try {
            definition = registeredDefinition.orElseThrow().withInputQuantity(operation.inputProduct().quantity());
        } catch (RuntimeException exception) {
            return TransformationPlan.rejected(
                    "invalid_transformation_definition",
                    "Registered transformation definition cannot be applied: " + exception.getMessage()
            );
        }
        TransformationContext context = new TransformationContext(
                List.of(new MaterialAmount(operation.inputProduct().typeId(), operation.inputProduct().quantity())),
                Optional.of(capability.toTransformationCapability())
        );
        TransformationEvaluation evaluation = TransformationEvaluator.evaluate(definition, context);
        return new TransformationPlan(definition, context, evaluation);
    }

    private static Optional<String> firstOutputMismatch(List<TransformationOutput> expectedOutputs, List<Product> committedOutputs) {
        if (expectedOutputs.size() != committedOutputs.size()) {
            return Optional.of("Transformation output count did not match committed output count");
        }
        for (int index = 0; index < expectedOutputs.size(); index++) {
            TransformationOutput expected = expectedOutputs.get(index);
            Product committed = committedOutputs.get(index);
            if (!expected.producedAmount().materialId().equals(committed.typeId())) {
                return Optional.of("Transformation output product did not match committed output product");
            }
            if (!expected.producedAmount().quantity().equals(committed.quantity())) {
                return Optional.of("Transformation output quantity did not match committed output quantity");
            }
        }
        return Optional.empty();
    }

    private static OperationResult transformationFailure(
            ResolvedWorkstationOperation operation,
            TransactionState state,
            String code,
            String message
    ) {
        return OperationResult.failure(
                operation.inputProduct(),
                state,
                new FailureReason(code, message),
                Optional.empty(),
                List.of(),
                List.of()
        );
    }

    private record TransformationPlan(
            TransformationDefinition definition,
            TransformationContext context,
            TransformationEvaluation evaluation,
            FailureReason failureReason
    ) {
        private TransformationPlan(
                TransformationDefinition definition,
                TransformationContext context,
                TransformationEvaluation evaluation
        ) {
            this(definition, context, evaluation, null);
        }

        static TransformationPlan rejected(String code, String message) {
            return new TransformationPlan(null, null, null, new FailureReason(code, message));
        }

        boolean hasFailure() {
            return failureReason != null;
        }
    }
}
