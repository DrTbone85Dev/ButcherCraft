package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryChangeValidation;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.binding.AuthoritativeTransactionResultEvidence;
import com.butchercraft.world.transaction.binding.TransactionBindingFailure;
import com.butchercraft.world.transaction.binding.TransactionBindingFailureCode;
import com.butchercraft.world.transaction.binding.TransactionBindingValidationResult;
import com.butchercraft.world.transaction.binding.TransactionProposalIdentity;
import com.butchercraft.world.transaction.binding.TransactionTerminalResult;
import com.butchercraft.world.transaction.binding.TransactionValidationBinding;
import com.butchercraft.world.transaction.binding.TransactionValidationBindingEvaluator;
import com.butchercraft.world.transaction.binding.TransactionValidationPlan;
import com.butchercraft.world.transaction.binding.ValidationConsumptionBoundary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TransactionExecutor {
    private final InventoryManager inventoryManager;
    private final TransactionValidationBindingEvaluator bindingEvaluator = new TransactionValidationBindingEvaluator();

    public TransactionExecutor(InventoryManager inventoryManager) {
        this.inventoryManager = Objects.requireNonNull(inventoryManager, "inventoryManager");
    }

    public TransactionResult execute(
            EconomicTransaction transaction,
            TransactionValidation acceptedValidation
    ) {
        Objects.requireNonNull(acceptedValidation, "acceptedValidation");
        TransactionResult preflight = validateExecutableStatus(transaction, acceptedValidation);
        if (preflight != null) {
            return preflight;
        }
        return TransactionResult.rejected(
                TransactionFailureCode.APPLICATION_WITHOUT_VALIDATION,
                List.of("Transaction execution requires Transaction-owned live validation authority"),
                transaction.simulationTick()
        );
    }

    TransactionResult execute(
            EconomicTransaction transaction,
            LiveTransactionValidation liveValidation
    ) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(liveValidation, "liveValidation");
        TransactionValidation acceptedValidation = liveValidation.validation();
        TransactionResult preflight = validateExecutableStatus(transaction, acceptedValidation);
        if (preflight != null) {
            return preflight;
        }

        Optional<TransactionValidationBinding> maybeBinding = acceptedValidation.binding();
        Optional<TransactionValidationPlan> maybePlan = acceptedValidation.validationPlan();
        if (maybeBinding.isEmpty()
                || acceptedValidation.proposalIdentity().isEmpty()
                || acceptedValidation.inventoryFreshnessIdentity().isEmpty()
                || maybePlan.isEmpty()) {
            return TransactionResult.rejected(
                    TransactionFailureCode.MISSING_VALIDATION_BINDING,
                    List.of("Transaction execution requires a complete accepted validation binding"),
                    transaction.simulationTick()
            );
        }

        TransactionValidationBinding binding = maybeBinding.orElseThrow();
        TransactionValidationPlan validationPlan = maybePlan.orElseThrow();
        if (!validationPlan.identityMatches()) {
            return rejectedWithEvidence(
                    binding,
                    TransactionFailureCode.VALIDATION_PLAN_MISMATCH,
                    List.of("Validation Plan Identity does not match the accepted validation plan content"),
                    transaction,
                    binding.inventoryFreshnessIdentity()
            );
        }

        TransactionValidationPlan recalculatedPlan = TransactionBindingFactory.validationPlan(
                binding.proposalIdentity(),
                binding.inventoryFreshnessIdentity(),
                acceptedValidation.inventoryChanges()
        );
        if (!recalculatedPlan.identity().equals(validationPlan.identity())) {
            return rejectedWithEvidence(
                    binding,
                    TransactionFailureCode.VALIDATION_PLAN_MISMATCH,
                    List.of("Accepted staged changes do not match the bound Validation Plan Identity"),
                    transaction,
                    binding.inventoryFreshnessIdentity()
            );
        }

        synchronized (inventoryManager) {
            TransactionProposalIdentity currentProposal = TransactionBindingFactory.proposalIdentity(transaction);
            InventoryFreshnessIdentity currentFreshness = TransactionBindingFactory.inventoryFreshnessIdentity(
                    inventoryManager,
                    transaction,
                    acceptedValidation.inventoryChanges()
            );
            TransactionBindingValidationResult bindingResult = bindingEvaluator.validateBindingAgainst(
                    binding,
                    currentProposal,
                    currentFreshness,
                    validationPlan.identity()
            );
            if (!bindingResult.successful()) {
                return rejectedWithEvidence(
                        binding,
                        mapBindingFailure(bindingResult.failures().getFirst().code()),
                        bindingResult.failures().stream().map(TransactionBindingFailure::message).toList(),
                        transaction,
                        currentFreshness
                );
            }

            InventoryChangeValidation currentValidation = inventoryManager.validateChanges(
                    acceptedValidation.inventoryChanges(),
                    transaction.simulationTick()
            );
            if (!currentValidation.isAllowed()) {
                return rejectedWithEvidence(
                        binding,
                        TransactionValidator.mapFailureCode(currentValidation.code()),
                        List.of(currentValidation.message()),
                        transaction,
                        currentFreshness
                );
            }

            TransactionBindingValidationResult consumed = ValidationConsumptionBoundary.consume(
                    TransactionExecutionAuthority.instance(),
                    liveValidation.consumptionGrant(),
                    binding
            );
            if (!consumed.successful()) {
                return rejectedWithEvidence(
                        binding,
                        mapBindingFailure(consumed.failures().getFirst().code()),
                        consumed.failures().stream().map(TransactionBindingFailure::message).toList(),
                        transaction,
                        currentFreshness
                );
            }

            try {
                List<TransactionAppliedChange> appliedChanges = inventoryManager.applyValidatedChanges(
                                TransactionExecutionAuthority.instance(),
                                acceptedValidation.inventoryChanges(),
                                transaction.simulationTick()
                        ).stream()
                        .map(TransactionAppliedChange::from)
                        .toList();
                InventoryFreshnessIdentity resultingFreshness = TransactionBindingFactory.inventoryFreshnessIdentity(
                        inventoryManager,
                        transaction,
                        acceptedValidation.inventoryChanges()
                );
                AuthoritativeTransactionResultEvidence evidence = TransactionBindingFactory.resultEvidence(
                        binding,
                        TransactionTerminalResult.APPLIED,
                        resultingFreshness
                );
                TransactionBindingValidationResult evidenceResult =
                        bindingEvaluator.validateResultEvidence(evidence, binding);
                if (!evidenceResult.successful()) {
                    return TransactionResult.rejected(
                            TransactionFailureCode.RESULT_EVIDENCE_MISMATCH,
                            evidenceResult.failures().stream().map(TransactionBindingFailure::message).toList(),
                            transaction.simulationTick(),
                            evidence
                    );
                }
                return TransactionResult.applied(appliedChanges, transaction.simulationTick(), evidence);
            } catch (IllegalStateException | ArithmeticException exception) {
                String message = exception.getMessage() == null
                        ? "Transaction execution failed after validation authority consumption"
                        : exception.getMessage();
                InventoryFreshnessIdentity resultingFreshness = TransactionBindingFactory.inventoryFreshnessIdentity(
                        inventoryManager,
                        transaction,
                        acceptedValidation.inventoryChanges()
                );
                return rejectedWithEvidence(
                        binding,
                        TransactionFailureCode.UNKNOWN,
                        List.of(message),
                        transaction,
                        resultingFreshness
                );
            }
        }
    }

    private static TransactionResult validateExecutableStatus(
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
        return null;
    }

    private static TransactionResult rejectedWithEvidence(
            TransactionValidationBinding binding,
            TransactionFailureCode failureCode,
            List<String> messages,
            EconomicTransaction transaction,
            InventoryFreshnessIdentity resultingFreshness
    ) {
        return TransactionResult.rejected(
                failureCode,
                messages,
                transaction.simulationTick(),
                TransactionBindingFactory.resultEvidence(binding, TransactionTerminalResult.REJECTED, resultingFreshness)
        );
    }

    private static TransactionFailureCode mapBindingFailure(TransactionBindingFailureCode code) {
        return switch (code) {
            case UNSUPPORTED_SCHEMA -> TransactionFailureCode.UNSUPPORTED_BINDING_SCHEMA;
            case MISSING_BINDING_COMPONENT, MISSING_VALIDATION_INPUT ->
                    TransactionFailureCode.MISSING_VALIDATION_BINDING;
            case PROPOSAL_IDENTITY_MISMATCH -> TransactionFailureCode.PROPOSAL_IDENTITY_MISMATCH;
            case INVENTORY_FRESHNESS_IDENTITY_MISMATCH -> TransactionFailureCode.INVENTORY_FRESHNESS_MISMATCH;
            case VALIDATION_PLAN_IDENTITY_MISMATCH -> TransactionFailureCode.VALIDATION_PLAN_MISMATCH;
            case VALIDATION_CONSUMPTION_AUTHORITY_CONSUMED ->
                    TransactionFailureCode.VALIDATION_AUTHORITY_CONSUMED;
            case VALIDATION_CONSUMPTION_AUTHORITY_INVALID -> TransactionFailureCode.VALIDATION_AUTHORITY_UNAVAILABLE;
            case RESULT_EVIDENCE_MISMATCH, RESULT_EVIDENCE_DIGEST_MISMATCH ->
                    TransactionFailureCode.RESULT_EVIDENCE_MISMATCH;
            case TRANSACTION_IDENTITY_CONFLICT, CONTENT_IDENTITY_CONFLICT ->
                    TransactionFailureCode.TRANSACTION_IDENTITY_CONFLICT;
            case INVALID_BINDING, HIDDEN_VALIDATION_INPUT -> TransactionFailureCode.VALIDATION_FAILED;
        };
    }
}
