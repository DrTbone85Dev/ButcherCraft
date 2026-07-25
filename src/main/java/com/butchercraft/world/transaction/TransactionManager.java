package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.transaction.binding.AuthoritativeTransactionResultEvidence;
import com.butchercraft.world.transaction.binding.TransactionTerminalResult;
import com.butchercraft.world.transaction.binding.TransactionProposalIdentity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TransactionManager {
    private final TransactionRegistry registry;
    private final TransactionValidator validator;
    private final TransactionExecutor executor;
    private final Map<TransactionId, TransactionProposalIdentity> proposalIdentities = new LinkedHashMap<>();
    private final Map<TransactionId, TransactionResult> authoritativeResults = new LinkedHashMap<>();

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
            proposalIdentities.put(transaction.id(), TransactionBindingFactory.proposalIdentity(transaction));
        }
    }

    public synchronized TransactionResult submit(EconomicTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransactionProposalIdentity proposalIdentity = TransactionBindingFactory.proposalIdentity(transaction);
        if (registry.contains(transaction.id())) {
            return duplicateOrConflict(transaction, proposalIdentity);
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
            proposalIdentities.put(transaction.id(), proposalIdentity);
            TransactionResult result = rejectedResult(validation, transaction.simulationTick());
            authoritativeResults.put(transaction.id(), result);
            return result;
        }

        EconomicTransaction validated = transaction.withStatus(TransactionStatus.VALIDATED);
        registry.replace(validated);
        TransactionResult result = executor.execute(validated, LiveTransactionValidation.issue(validation));
        registry.replace(validated.withStatus(
                result.success() ? TransactionStatus.APPLIED : TransactionStatus.REJECTED
        ));
        proposalIdentities.put(transaction.id(), validation.proposalIdentity().orElse(proposalIdentity));
        authoritativeResults.put(transaction.id(), result);
        return result;
    }

    public synchronized Optional<EconomicTransaction> find(TransactionId id) {
        return registry.find(id);
    }

    public synchronized Optional<TransactionResult> resultFor(TransactionId id) {
        return Optional.ofNullable(authoritativeResults.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Optional<AuthoritativeTransactionResultEvidence> resultEvidenceFor(TransactionId id) {
        return resultFor(id).flatMap(TransactionResult::resultEvidence);
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
        TransactionResult result = replayExecutor.execute(accepted, LiveTransactionValidation.issue(validation));
        if (!result.success()) {
            throw new IllegalStateException(
                    "Transaction replay execution failed for " + historical.id().value() + ": "
                            + String.join("; ", result.validationMessages())
            );
        }
        return result;
    }

    private TransactionResult duplicateOrConflict(
            EconomicTransaction submitted,
            TransactionProposalIdentity submittedProposalIdentity
    ) {
        TransactionProposalIdentity existingProposal = proposalIdentities.get(submitted.id());
        if (existingProposal == null) {
            EconomicTransaction existing = registry.find(submitted.id()).orElseThrow();
            existingProposal = TransactionBindingFactory.proposalIdentity(existing);
            proposalIdentities.put(existing.id(), existingProposal);
        }
        if (existingProposal.equals(submittedProposalIdentity)) {
            TransactionResult existingResult = authoritativeResults.get(submitted.id());
            if (existingResult != null) {
                return TransactionResult.duplicateObservation(existingResult, submitted.simulationTick());
            }
            return TransactionResult.rejected(
                    TransactionFailureCode.PERSISTENCE_COMPATIBILITY_FAILURE,
                    List.of("Existing Transaction has no live authoritative result evidence: "
                            + submitted.id().value()),
                    submitted.simulationTick()
            );
        }
        Optional<AuthoritativeTransactionResultEvidence> existingEvidence =
                resultEvidenceFor(submitted.id());
        return TransactionResult.conflict(
                TransactionFailureCode.TRANSACTION_IDENTITY_CONFLICT,
                List.of("Same Transaction identity cannot authorize a different canonical proposal identity: "
                        + submitted.id().value()),
                submitted.simulationTick(),
                existingEvidence
        );
    }

    private static TransactionResult rejectedResult(TransactionValidation validation, long executionTick) {
        if (validation.binding().isPresent() && validation.inventoryFreshnessIdentity().isPresent()) {
            return TransactionResult.rejected(
                    validation.failureCode().orElse(TransactionFailureCode.UNKNOWN),
                    validation.messages(),
                    executionTick,
                    TransactionBindingFactory.resultEvidence(
                            validation.binding().orElseThrow(),
                            TransactionTerminalResult.REJECTED,
                            validation.inventoryFreshnessIdentity().orElseThrow()
                    )
            );
        }
        return TransactionResult.rejected(
                validation.failureCode().orElse(TransactionFailureCode.UNKNOWN),
                validation.messages(),
                executionTick
        );
    }
}
