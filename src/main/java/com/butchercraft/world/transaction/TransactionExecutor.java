package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryChangeValidation;
import com.butchercraft.world.inventory.InventoryManager;

import java.util.List;
import java.util.Objects;

public final class TransactionExecutor {
    private final InventoryManager inventoryManager;

    public TransactionExecutor(InventoryManager inventoryManager) {
        this.inventoryManager = Objects.requireNonNull(inventoryManager, "inventoryManager");
    }

    public TransactionResult execute(
            EconomicTransaction transaction,
            TransactionValidation acceptedValidation
    ) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(acceptedValidation, "acceptedValidation");
        if (transaction.status() != TransactionStatus.VALIDATED) {
            return TransactionResult.rejected(
                    TransactionFailureCode.INVALID_STATUS,
                    List.of("Transaction must be validated before execution"),
                    transaction.simulationTick()
            );
        }
        if (!acceptedValidation.accepted() || !acceptedValidation.transactionId().equals(transaction.id())) {
            return TransactionResult.rejected(
                    TransactionFailureCode.VALIDATION_FAILED,
                    List.of("Transaction execution requires its previously accepted validation"),
                    transaction.simulationTick()
            );
        }

        InventoryChangeValidation currentValidation = inventoryManager.validateChanges(
                acceptedValidation.inventoryChanges(),
                transaction.simulationTick()
        );
        if (!currentValidation.isAllowed()) {
            return TransactionResult.rejected(
                    TransactionValidator.mapFailureCode(currentValidation.code()),
                    List.of(currentValidation.message()),
                    transaction.simulationTick()
            );
        }

        try {
            return TransactionResult.applied(
                    inventoryManager.applyValidatedChanges(
                                    TransactionExecutionAuthority.instance(),
                                    acceptedValidation.inventoryChanges(),
                                    transaction.simulationTick()
                            ).stream()
                            .map(TransactionAppliedChange::from)
                            .toList(),
                    transaction.simulationTick()
            );
        } catch (IllegalStateException | ArithmeticException exception) {
            String message = exception.getMessage() == null
                    ? "Transaction execution failed"
                    : exception.getMessage();
            return TransactionResult.rejected(
                    TransactionFailureCode.UNKNOWN,
                    List.of(message),
                    transaction.simulationTick()
            );
        }
    }
}
