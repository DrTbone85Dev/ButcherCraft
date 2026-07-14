package com.butchercraft.engine.transaction;

import com.butchercraft.engine.modifier.ModifierApplication;
import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ModifierSystem;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.result.OperationWarning;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Transaction-safe domain processor for one input product and one operation.
 *
 * <p>This class is the deliberate mutable owner in the engine. It validates, prepares, commits,
 * cancels, or rejects one operation through explicit transitions. It never consumes Minecraft
 * inventories, creates item stacks, or touches world state; future integration code can inspect
 * the proposed or committed output and perform server-side inventory changes around it.</p>
 */
public final class ProcessingTransaction {
    private final Product input;
    private final ProcessingOperation operation;
    private TransactionState state = TransactionState.CREATED;
    private Product proposedOutput;
    private Product committedOutput;
    private FailureReason lastFailure;
    private List<ProcessingModifier> appliedModifiers = List.of();
    private List<OperationWarning> warnings = List.of();

    private ProcessingTransaction(Product input, ProcessingOperation operation) {
        this.input = Objects.requireNonNull(input, "input");
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    public static ProcessingTransaction create(Product input, ProcessingOperation operation) {
        return new ProcessingTransaction(input, operation);
    }

    public Product input() {
        return input;
    }

    public ProcessingOperation operation() {
        return operation;
    }

    public TransactionState state() {
        return state;
    }

    public Optional<Product> proposedOutput() {
        return Optional.ofNullable(proposedOutput);
    }

    public Optional<Product> committedOutput() {
        return Optional.ofNullable(committedOutput);
    }

    public Optional<FailureReason> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }

    /**
     * Validates that the input product matches the operation requirements.
     *
     * @return success in VALIDATED state or rejection with an explicit reason
     */
    public OperationResult validate() {
        if (state == TransactionState.VALIDATED) {
            return success(Optional.empty(), Optional.empty());
        }
        if (state != TransactionState.CREATED) {
            return failWithoutStateChange("invalid_validate_state", "Validation is only allowed before preparation");
        }
        if (!input.typeId().equals(operation.requiredProductType())) {
            return reject("wrong_product_type", "Input product type does not match operation requirement");
        }
        if (input.processingState() != operation.requiredProcessingState()) {
            return reject("wrong_processing_state", "Input processing state does not match operation requirement");
        }
        state = TransactionState.VALIDATED;
        return success(Optional.empty(), Optional.empty());
    }

    /**
     * Prepares a proposed output without committing it.
     *
     * @return success with proposed output, or failure/rejection without consuming input
     */
    public OperationResult prepare() {
        if (state == TransactionState.CREATED) {
            OperationResult validation = validate();
            if (!validation.succeeded()) {
                return validation;
            }
        }
        if (state != TransactionState.VALIDATED) {
            return failWithoutStateChange("invalid_prepare_state", "Preparation is only allowed after validation");
        }

        try {
            ModifierApplication application = ModifierSystem.apply(operation.modifiers());
            appliedModifiers = application.appliedModifiers();
            warnings = appliedModifiers.stream()
                    .filter(modifier -> modifier.category() == ModifierCategory.WARNING)
                    .map(modifier -> new OperationWarning(modifier.id(), modifier.reason()))
                    .toList();

            ProductQuantity resultingQuantity = operation.baseYield().apply(input.quantity());
            int qualityDelta = Math.addExact(operation.baseQualityDelta(), application.qualityDelta());
            ProductQuality resultingQuality = input.quality().adjustedByClamped(qualityDelta);
            proposedOutput = new Product(
                    operation.outputProductType(),
                    input.sourceCategory(),
                    operation.outputProcessingState(),
                    resultingQuantity,
                    resultingQuality
            );
            state = TransactionState.PREPARED;
            return success(Optional.of(proposedOutput), Optional.empty());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            state = TransactionState.FAILED;
            lastFailure = new FailureReason("preparation_failed", exception.getMessage());
            return failure(Optional.ofNullable(proposedOutput));
        }
    }

    /**
     * Commits a previously prepared output exactly once.
     *
     * @return committed success or a safe failure for illegal repeat/terminal states
     */
    public OperationResult commit() {
        if (state == TransactionState.PREPARED) {
            committedOutput = proposedOutput;
            state = TransactionState.COMMITTED;
            return success(Optional.of(proposedOutput), Optional.of(committedOutput));
        }
        if (state == TransactionState.COMMITTED) {
            return failWithoutStateChange("already_committed", "Transaction output was already committed");
        }
        if (state == TransactionState.CANCELLED) {
            return failWithoutStateChange("cancelled", "Cancelled transactions cannot be committed");
        }
        return failWithoutStateChange("not_prepared", "Transaction must be prepared before commit");
    }

    /**
     * Cancels the transaction before commit.
     *
     * @return cancellation failure result with any proposed output retained for inspection
     */
    public OperationResult cancel() {
        if (state == TransactionState.COMMITTED) {
            return failWithoutStateChange("already_committed", "Committed transactions cannot be cancelled");
        }
        if (state.isFailureTerminal()) {
            return failWithoutStateChange("already_terminal", "Transaction is already terminal");
        }
        state = TransactionState.CANCELLED;
        lastFailure = new FailureReason("cancelled", "Transaction was cancelled before commit");
        return failure(Optional.ofNullable(proposedOutput));
    }

    private OperationResult reject(String code, String message) {
        state = TransactionState.REJECTED;
        lastFailure = new FailureReason(code, message);
        return failure(Optional.empty());
    }

    private OperationResult failWithoutStateChange(String code, String message) {
        lastFailure = new FailureReason(code, message);
        return failure(Optional.ofNullable(proposedOutput));
    }

    private OperationResult success(Optional<Product> proposed, Optional<Product> committed) {
        return OperationResult.success(input, state, proposed, committed, appliedModifiers, warnings);
    }

    private OperationResult failure(Optional<Product> proposed) {
        return OperationResult.failure(input, state, lastFailure, proposed, appliedModifiers, warnings);
    }
}
