package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TransactionManager {
    private final TransactionRegistry registry;
    private final TransactionValidator validator;
    private final TransactionExecutor executor;

    public TransactionManager(InventoryManager inventoryManager) {
        this(new TransactionRegistry(), inventoryManager);
    }

    public TransactionManager(TransactionRegistry loadedRegistry, InventoryManager inventoryManager) {
        Objects.requireNonNull(inventoryManager, "inventoryManager");
        this.registry = new TransactionRegistry(Objects.requireNonNull(loadedRegistry, "loadedRegistry").history());
        this.validator = new TransactionValidator(inventoryManager);
        this.executor = new TransactionExecutor(inventoryManager);
        for (EconomicTransaction transaction : registry.history()) {
            TransactionValidation references = validator.validateReferences(transaction);
            if (!references.accepted()) {
                throw new IllegalArgumentException(
                        "Persisted transaction is not valid: " + transaction.id().value() + ": "
                                + String.join("; ", references.messages())
                );
            }
        }
    }

    public synchronized TransactionResult submit(EconomicTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (registry.contains(transaction.id())) {
            return TransactionResult.rejected(
                    TransactionFailureCode.VALIDATION_FAILED,
                    List.of("Duplicate transaction id: " + transaction.id().value()),
                    transaction.simulationTick()
            );
        }
        if (transaction.status() != TransactionStatus.PENDING) {
            return TransactionResult.rejected(
                    TransactionFailureCode.INVALID_STATUS,
                    List.of("Submitted transaction status must be pending"),
                    transaction.simulationTick()
            );
        }

        TransactionValidation references = validator.validateReferences(transaction);
        if (!references.accepted()) {
            return rejectedResult(references, transaction.simulationTick());
        }
        registry.register(transaction);

        TransactionValidation validation = validator.validateForSubmission(transaction);
        if (!validation.accepted()) {
            registry.replace(transaction.withStatus(TransactionStatus.REJECTED));
            return rejectedResult(validation, transaction.simulationTick());
        }

        EconomicTransaction validated = transaction.withStatus(TransactionStatus.VALIDATED);
        registry.replace(validated);
        TransactionResult result = executor.execute(validated, validation);
        registry.replace(validated.withStatus(
                result.success() ? TransactionStatus.APPLIED : TransactionStatus.REJECTED
        ));
        return result;
    }

    public synchronized Optional<EconomicTransaction> find(TransactionId id) {
        return registry.find(id);
    }

    public synchronized int size() {
        return registry.size();
    }

    public synchronized List<EconomicTransaction> history() {
        return registry.history();
    }

    public synchronized List<EconomicTransaction> findByType(TransactionType type) {
        return registry.findByType(type);
    }

    public synchronized List<EconomicTransaction> findByStatus(TransactionStatus status) {
        return registry.findByStatus(status);
    }

    public synchronized List<TransactionResult> replayInto(InventoryManager baselineInventory) {
        Objects.requireNonNull(baselineInventory, "baselineInventory");
        TransactionValidator replayValidator = new TransactionValidator(baselineInventory);
        TransactionExecutor replayExecutor = new TransactionExecutor(baselineInventory);
        return registry.history().stream()
                .filter(transaction -> transaction.status() == TransactionStatus.APPLIED)
                .map(transaction -> replay(transaction, replayValidator, replayExecutor))
                .toList();
    }

    private static TransactionResult replay(
            EconomicTransaction historical,
            TransactionValidator replayValidator,
            TransactionExecutor replayExecutor
    ) {
        EconomicTransaction accepted = historical.withStatus(TransactionStatus.VALIDATED);
        TransactionValidation validation = replayValidator.validateForExecution(accepted);
        if (!validation.accepted()) {
            throw new IllegalStateException(
                    "Transaction replay validation failed for " + historical.id().value() + ": "
                            + String.join("; ", validation.messages())
            );
        }
        TransactionResult result = replayExecutor.execute(accepted, validation);
        if (!result.success()) {
            throw new IllegalStateException(
                    "Transaction replay execution failed for " + historical.id().value() + ": "
                            + String.join("; ", result.validationMessages())
            );
        }
        return result;
    }

    private static TransactionResult rejectedResult(TransactionValidation validation, long executionTick) {
        return TransactionResult.rejected(
                validation.failureCode().orElse(TransactionFailureCode.UNKNOWN),
                validation.messages(),
                executionTick
        );
    }
}
