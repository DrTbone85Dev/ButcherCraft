package com.butchercraft.engine.result;

import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.transaction.TransactionState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit immutable outcome for a processing operation.
 *
 * <p>Results are either success or failure; booleans and null are not used to encode domain
 * failure. Success cannot contain a failure reason, and failure cannot contain committed output.
 * The type is Minecraft-independent so tests can exercise transaction behavior without launching
 * the game.</p>
 */
public sealed interface OperationResult permits OperationResult.Success, OperationResult.Failure {
    Product input();

    boolean succeeded();

    Optional<FailureReason> failureReason();

    Optional<Product> proposedOutput();

    Optional<Product> committedOutput();

    List<ProcessingModifier> appliedModifiers();

    Optional<ProductQuality> resultingQuality();

    Optional<ProductQuantity> resultingQuantity();

    List<OperationWarning> warnings();

    TransactionState transactionState();

    static Success success(
            Product input,
            TransactionState transactionState,
            Optional<Product> proposedOutput,
            Optional<Product> committedOutput,
            List<ProcessingModifier> appliedModifiers,
            List<OperationWarning> warnings
    ) {
        return new Success(input, proposedOutput, committedOutput, appliedModifiers, warnings, transactionState);
    }

    static Failure failure(
            Product input,
            TransactionState transactionState,
            FailureReason failureReason,
            Optional<Product> proposedOutput,
            List<ProcessingModifier> appliedModifiers,
            List<OperationWarning> warnings
    ) {
        return new Failure(input, failureReason, proposedOutput, appliedModifiers, warnings, transactionState);
    }

    record Success(
            Product input,
            Optional<Product> proposedOutput,
            Optional<Product> committedOutput,
            List<ProcessingModifier> appliedModifiers,
            List<OperationWarning> warnings,
            TransactionState transactionState
    ) implements OperationResult {
        public Success {
            Objects.requireNonNull(input, "input");
            proposedOutput = Objects.requireNonNull(proposedOutput, "proposedOutput");
            committedOutput = Objects.requireNonNull(committedOutput, "committedOutput");
            appliedModifiers = List.copyOf(Objects.requireNonNull(appliedModifiers, "appliedModifiers"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            Objects.requireNonNull(transactionState, "transactionState");
            if (transactionState.isFailureTerminal()) {
                throw new IllegalArgumentException("Success cannot use a failure terminal transaction state");
            }
            if (transactionState == TransactionState.PREPARED && proposedOutput.isEmpty()) {
                throw new IllegalArgumentException("Prepared success requires proposed output");
            }
            if (transactionState == TransactionState.COMMITTED && committedOutput.isEmpty()) {
                throw new IllegalArgumentException("Committed success requires committed output");
            }
            if (committedOutput.isPresent() && transactionState != TransactionState.COMMITTED) {
                throw new IllegalArgumentException("Committed output is only valid for committed success");
            }
        }

        @Override
        public boolean succeeded() {
            return true;
        }

        @Override
        public Optional<FailureReason> failureReason() {
            return Optional.empty();
        }

        @Override
        public Optional<ProductQuality> resultingQuality() {
            return committedOutput.or(() -> proposedOutput).map(Product::quality);
        }

        @Override
        public Optional<ProductQuantity> resultingQuantity() {
            return committedOutput.or(() -> proposedOutput).map(Product::quantity);
        }
    }

    record Failure(
            Product input,
            FailureReason reason,
            Optional<Product> proposedOutput,
            List<ProcessingModifier> appliedModifiers,
            List<OperationWarning> warnings,
            TransactionState transactionState
    ) implements OperationResult {
        public Failure {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(reason, "reason");
            proposedOutput = Objects.requireNonNull(proposedOutput, "proposedOutput");
            appliedModifiers = List.copyOf(Objects.requireNonNull(appliedModifiers, "appliedModifiers"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            Objects.requireNonNull(transactionState, "transactionState");
        }

        @Override
        public boolean succeeded() {
            return false;
        }

        @Override
        public Optional<FailureReason> failureReason() {
            return Optional.of(reason);
        }

        @Override
        public Optional<Product> committedOutput() {
            return Optional.empty();
        }

        @Override
        public Optional<ProductQuality> resultingQuality() {
            return proposedOutput.map(Product::quality);
        }

        @Override
        public Optional<ProductQuantity> resultingQuantity() {
            return proposedOutput.map(Product::quantity);
        }
    }
}
