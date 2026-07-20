package com.butchercraft.transformation;

import java.util.List;
import java.util.Objects;

/**
 * Pure Java material transaction that commits transformation input and output changes atomically.
 */
public final class TransformationTransaction {
    private final TransformationDefinition definition;
    private final TransformationContext context;
    private final TransformationEvaluation evaluation;
    private final TransformationMaterialStore inputStore;
    private final TransformationMaterialStore outputStore;

    private TransformationTransactionState state = TransformationTransactionState.CREATED;
    private TransformationExecution preparedExecution;
    private TransformationExecution lastResult;

    private TransformationTransaction(
            TransformationDefinition definition,
            TransformationContext context,
            TransformationEvaluation evaluation,
            TransformationMaterialStore inputStore,
            TransformationMaterialStore outputStore
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.context = Objects.requireNonNull(context, "context");
        this.evaluation = Objects.requireNonNull(evaluation, "evaluation");
        this.inputStore = Objects.requireNonNull(inputStore, "inputStore");
        this.outputStore = Objects.requireNonNull(outputStore, "outputStore");
    }

    public static TransformationTransaction create(
            TransformationDefinition definition,
            TransformationContext context,
            TransformationEvaluation evaluation,
            TransformationMaterialStore inputStore,
            TransformationMaterialStore outputStore
    ) {
        return new TransformationTransaction(definition, context, evaluation, inputStore, outputStore);
    }

    public TransformationTransactionState state() {
        return state;
    }

    public TransformationExecution prepare() {
        if (state == TransformationTransactionState.COMMITTED) {
            return reject(
                    TransformationExecutionCode.TRANSACTION_ALREADY_COMMITTED,
                    "Transformation transaction was already committed"
            );
        }
        if (state == TransformationTransactionState.PREPARED) {
            return preparedExecution;
        }
        if (state == TransformationTransactionState.REJECTED || state == TransformationTransactionState.ROLLED_BACK) {
            return lastResult;
        }

        TransformationExecution execution = TransformationExecutor.execute(definition, context, evaluation);
        if (!execution.succeeded()) {
            state = TransformationTransactionState.REJECTED;
            lastResult = execution;
            return execution;
        }

        TransformationExecution storeValidation = validateStores(execution.outputs());
        if (!storeValidation.succeeded()) {
            state = TransformationTransactionState.REJECTED;
            lastResult = storeValidation;
            return storeValidation;
        }

        state = TransformationTransactionState.PREPARED;
        preparedExecution = execution;
        lastResult = execution;
        return execution;
    }

    public TransformationExecution commit() {
        if (state == TransformationTransactionState.COMMITTED) {
            return reject(
                    TransformationExecutionCode.TRANSACTION_ALREADY_COMMITTED,
                    "Transformation transaction was already committed"
            );
        }
        if (state == TransformationTransactionState.REJECTED || state == TransformationTransactionState.ROLLED_BACK) {
            return lastResult;
        }
        TransformationExecution prepared = state == TransformationTransactionState.PREPARED ? preparedExecution : prepare();
        if (!prepared.succeeded()) {
            return prepared;
        }

        TransformationExecution currentValidation = validateStores(prepared.outputs());
        if (!currentValidation.succeeded()) {
            state = TransformationTransactionState.REJECTED;
            lastResult = currentValidation;
            return currentValidation;
        }

        TransformationMaterialStoreSnapshot inputSnapshot = inputStore.snapshot();
        TransformationMaterialStoreSnapshot outputSnapshot = outputStore.snapshot();
        try {
            for (TransformationInput input : definition.inputs()) {
                inputStore.extract(input.requiredAmount());
            }
            for (TransformationOutput output : prepared.outputs()) {
                outputStore.insert(output.producedAmount());
            }
        } catch (RuntimeException exception) {
            inputStore.restore(inputSnapshot);
            outputStore.restore(outputSnapshot);
            state = TransformationTransactionState.ROLLED_BACK;
            lastResult = TransformationExecution.rejected(
                    TransformationExecutionCode.TRANSACTION_ROLLED_BACK,
                    "Transformation transaction rolled back: " + exception.getMessage()
            );
            return lastResult;
        }

        state = TransformationTransactionState.COMMITTED;
        lastResult = prepared;
        return prepared;
    }

    private TransformationExecution validateStores(List<TransformationOutput> outputs) {
        List<MaterialAmount> requiredInputs = definition.inputs().stream()
                .map(TransformationInput::requiredAmount)
                .toList();
        if (!inputStore.canExtractAll(requiredInputs)) {
            return TransformationExecution.rejected(
                    TransformationExecutionCode.INPUT_UNAVAILABLE,
                    "Transformation input store cannot provide all required inputs"
            );
        }

        List<MaterialAmount> producedOutputs = outputs.stream()
                .map(TransformationOutput::producedAmount)
                .toList();
        if (!outputStore.canInsertAll(producedOutputs)) {
            return TransformationExecution.rejected(
                    TransformationExecutionCode.OUTPUT_REJECTED,
                    "Transformation output store cannot accept all produced outputs"
            );
        }
        return TransformationExecution.executed(definition);
    }

    private TransformationExecution reject(TransformationExecutionCode code, String message) {
        lastResult = TransformationExecution.rejected(code, message);
        return lastResult;
    }
}
