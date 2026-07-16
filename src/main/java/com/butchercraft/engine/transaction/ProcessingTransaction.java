package com.butchercraft.engine.transaction;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.evaluation.ProcessingEvaluator;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationOutputResult;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.result.OperationWarning;
import com.butchercraft.engine.validation.ValidationSummary;

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
    private final ProcessingContext context;
    private TransactionState state = TransactionState.CREATED;
    private List<OperationOutputResult> proposedOutputResults = List.of();
    private List<OperationOutputResult> committedOutputResults = List.of();
    private FailureReason lastFailure;
    private List<ProcessingModifier> appliedModifiers = List.of();
    private List<OperationWarning> warnings = List.of();

    private ProcessingTransaction(Product input, ProcessingOperation operation, ProcessingContext context) {
        this.input = Objects.requireNonNull(input, "input");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.context = Objects.requireNonNull(context, "context");
    }

    public static ProcessingTransaction create(Product input, ProcessingOperation operation) {
        return new ProcessingTransaction(input, operation, ProcessingContext.neutral(input, operation));
    }

    public static ProcessingTransaction create(ProcessingContext context) {
        Objects.requireNonNull(context, "context");
        return new ProcessingTransaction(context.inputProduct(), context.operation(), context);
    }

    public Product input() {
        return input;
    }

    public ProcessingOperation operation() {
        return operation;
    }

    public ProcessingContext context() {
        return context;
    }

    public TransactionState state() {
        return state;
    }

    public Optional<Product> proposedOutput() {
        return proposedOutputResults.stream().findFirst().map(OperationOutputResult::product);
    }

    public Optional<Product> committedOutput() {
        return committedOutputResults.stream().findFirst().map(OperationOutputResult::product);
    }

    public List<Product> proposedOutputs() {
        return proposedOutputResults.stream().map(OperationOutputResult::product).toList();
    }

    public List<Product> committedOutputs() {
        return committedOutputResults.stream().map(OperationOutputResult::product).toList();
    }

    public List<OperationOutputResult> proposedOutputResults() {
        return proposedOutputResults;
    }

    public List<OperationOutputResult> committedOutputResults() {
        return committedOutputResults;
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
            return success(List.of(), List.of());
        }
        if (state != TransactionState.CREATED) {
            return failWithoutStateChange("invalid_validate_state", "Validation is only allowed before preparation");
        }
        ValidationSummary validation = ProcessingEvaluator.validate(operation, context);
        warnings = validation.warnings();
        if (!validation.accepted()) {
            return reject(validation.rejectionReason().orElseThrow());
        }
        state = TransactionState.VALIDATED;
        return success(List.of(), List.of());
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
            OperationResult prepared = ProcessingEvaluator.prepare(operation, context);
            appliedModifiers = prepared.appliedModifiers();
            warnings = prepared.warnings();
            if (!prepared.succeeded()) {
                state = prepared.transactionState();
                lastFailure = prepared.failureReason().orElseThrow();
                return failure(prepared.proposedOutputResults());
            }
            proposedOutputResults = prepared.proposedOutputResults();
            state = TransactionState.PREPARED;
            return success(proposedOutputResults, List.of());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            state = TransactionState.FAILED;
            lastFailure = new FailureReason("preparation_failed", exception.getMessage());
            return failure(proposedOutputResults);
        }
    }

    /**
     * Commits a previously prepared output exactly once.
     *
     * @return committed success or a safe failure for illegal repeat/terminal states
     */
    public OperationResult commit() {
        if (state == TransactionState.PREPARED) {
            committedOutputResults = proposedOutputResults;
            state = TransactionState.COMMITTED;
            return success(proposedOutputResults, committedOutputResults);
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
        return failure(proposedOutputResults);
    }

    private OperationResult reject(FailureReason reason) {
        state = TransactionState.REJECTED;
        lastFailure = reason;
        return failure(List.of());
    }

    private OperationResult failWithoutStateChange(String code, String message) {
        lastFailure = new FailureReason(code, message);
        return failure(proposedOutputResults);
    }

    private OperationResult success(List<OperationOutputResult> proposed, List<OperationOutputResult> committed) {
        return OperationResult.successOutputs(input, state, proposed, committed, appliedModifiers, warnings);
    }

    private OperationResult failure(List<OperationOutputResult> proposed) {
        return OperationResult.failureOutputs(input, state, lastFailure, proposed, appliedModifiers, warnings);
    }
}
