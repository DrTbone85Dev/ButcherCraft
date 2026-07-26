package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import com.butchercraft.world.inventory.freshness.InventoryFreshnessComponent;
import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionStatus;
import com.butchercraft.world.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionValidationBindingFoundationTest {
    private static final TransactionValidationBindingEvaluator EVALUATOR =
            new TransactionValidationBindingEvaluator();

    @Test
    void proposalIdentityIsStableForIdenticalCanonicalContent() {
        TransactionProposalIdentity first = TransactionProposalIdentity.from(transaction("test:tx/proposal", 5L));
        TransactionProposalIdentity second = TransactionProposalIdentity.from(transaction("test:tx/proposal", 5L));

        assertEquals(first, second);
    }

    @Test
    void changedProposalContentChangesProposalIdentity() {
        TransactionProposalIdentity first = TransactionProposalIdentity.from(transaction("test:tx/proposal", 5L));
        TransactionProposalIdentity second = TransactionProposalIdentity.from(transaction("test:tx/proposal", 6L));

        assertEquals(first.transactionId(), second.transactionId());
        assertNotEquals(first.proposalDigest(), second.proposalDigest());
    }

    @Test
    void transactionStatusDoesNotChangeProposalIdentity() {
        EconomicTransaction proposal = transaction("test:tx/status", 5L);

        assertEquals(
                TransactionProposalIdentity.from(proposal),
                TransactionProposalIdentity.from(proposal.withStatus(TransactionStatus.VALIDATED))
        );
    }

    @Test
    void explicitValidationInputsAreSortedForCanonicalBindingIdentity() {
        ValidationInputIdentity firstInput = input("test:input/a", digest("input-a"));
        ValidationInputIdentity secondInput = input("test:input/b", digest("input-b"));
        TransactionValidationBinding first = binding(
                transaction("test:tx/inputs", 5L),
                List.of(firstInput, secondInput)
        );
        TransactionValidationBinding second = binding(
                transaction("test:tx/inputs", 5L),
                List.of(secondInput, firstInput)
        );

        assertEquals(first.validationInputIdentities(), second.validationInputIdentities());
        assertEquals(first, second);
    }

    @Test
    void orderedStagedChangesPreservePlanOrder() {
        InventoryChange add = InventoryChange.add(
                InventoryTestFixtures.BEEF_INVENTORY,
                new InventoryEntry(InventoryTestFixtures.BEEF, 5L, UnitOfMeasure.POUND)
        );
        InventoryChange remove = InventoryChange.remove(
                InventoryTestFixtures.GRAIN_INVENTORY,
                new InventoryEntry(InventoryTestFixtures.GRAIN, 1L, UnitOfMeasure.BUSHEL)
        );

        TransactionValidationPlan addThenRemove = TransactionValidationPlan.fromInventoryChanges(
                List.of(add, remove),
                List.of()
        );
        TransactionValidationPlan removeThenAdd = TransactionValidationPlan.fromInventoryChanges(
                List.of(remove, add),
                List.of()
        );

        assertTrue(addThenRemove.identityMatches());
        assertTrue(removeThenAdd.identityMatches());
        assertNotEquals(addThenRemove.identity(), removeThenAdd.identity());
    }

    @Test
    void validationPlanIdentityChangesWhenStagedMutationChanges() {
        TransactionValidationPlan first = TransactionValidationPlan.fromInventoryChanges(
                List.of(InventoryChange.add(
                        InventoryTestFixtures.BEEF_INVENTORY,
                        new InventoryEntry(InventoryTestFixtures.BEEF, 5L, UnitOfMeasure.POUND)
                )),
                List.of()
        );
        TransactionValidationPlan second = TransactionValidationPlan.fromInventoryChanges(
                List.of(InventoryChange.add(
                        InventoryTestFixtures.BEEF_INVENTORY,
                        new InventoryEntry(InventoryTestFixtures.BEEF, 6L, UnitOfMeasure.POUND)
                )),
                List.of()
        );

        assertNotEquals(first.identity(), second.identity());
    }

    @Test
    void successfulBindingContainsIndependentIdentities() {
        EconomicTransaction transaction = transaction("test:tx/binding", 5L);
        TransactionValidationBinding binding = binding(transaction, List.of(input("test:input/a", digest("input-a"))));
        TransactionBindingValidationResult result = EVALUATOR.validateBinding(
                binding,
                binding.validationInputIdentities()
        );

        assertTrue(result.successful());
        assertEquals(transaction.id(), binding.transactionId());
        assertEquals(transaction.id(), binding.proposalIdentity().transactionId());
        assertTrue(binding.inventoryFreshnessIdentity().digestMatches());
    }

    @Test
    void missingBindingComponentFailsTyped() {
        TransactionValidationBinding binding = binding(transaction("test:tx/missing", 5L), List.of());
        TransactionValidationBindingCandidate candidate = new TransactionValidationBindingCandidate(
                TransactionBindingSchema.CURRENT_VERSION,
                Optional.of(binding.transactionId()),
                Optional.empty(),
                Optional.of(binding.inventoryFreshnessIdentity()),
                Optional.of(binding.validationPlanIdentity()),
                List.of()
        );

        TransactionBindingValidationResult result = EVALUATOR.validateBindingCandidate(candidate, List.of());

        assertFalse(result.successful());
        assertTrue(contains(result, TransactionBindingFailureCode.MISSING_BINDING_COMPONENT));
    }

    @Test
    void hiddenValidationInputFailsTyped() {
        TransactionValidationBinding binding = binding(
                transaction("test:tx/hidden_input", 5L),
                List.of(input("test:input/hidden", digest("hidden")))
        );

        TransactionBindingValidationResult result = EVALUATOR.validateBinding(binding, List.of());

        assertFalse(result.successful());
        assertTrue(contains(result, TransactionBindingFailureCode.HIDDEN_VALIDATION_INPUT));
    }

    @Test
    void missingExplicitValidationInputFailsTyped() {
        TransactionValidationBinding binding = binding(transaction("test:tx/missing_input", 5L), List.of());

        TransactionBindingValidationResult result = EVALUATOR.validateBinding(
                binding,
                List.of(input("test:input/required", digest("required")))
        );

        assertFalse(result.successful());
        assertTrue(contains(result, TransactionBindingFailureCode.MISSING_VALIDATION_INPUT));
    }

    @Test
    void singleUseAuthorityCannotBeConsumedTwice() {
        TransactionValidationBinding binding = binding(transaction("test:tx/authority", 5L), List.of());
        ValidationConsumptionAuthority authority = ValidationConsumptionAuthority.issue(
                binding,
                "butchercraft:transaction_validation"
        );

        assertTrue(authority.consume(binding).successful());
        ValidationConsumptionResult second = authority.consume(binding);

        assertFalse(second.successful());
        assertTrue(second.failures().stream().anyMatch(failure ->
                failure.code() == TransactionBindingFailureCode.VALIDATION_CONSUMPTION_AUTHORITY_CONSUMED));
    }

    @Test
    void authorityDoesNotAcceptDifferentBinding() {
        TransactionValidationBinding binding = binding(transaction("test:tx/authority_mismatch", 5L), List.of());
        ValidationConsumptionAuthority authority = ValidationConsumptionAuthority.issue(
                binding,
                "butchercraft:transaction_validation"
        );

        ValidationConsumptionResult result = authority.consume(
                binding(transaction("test:tx/authority_mismatch", 6L), List.of())
        );

        assertFalse(result.successful());
        assertTrue(result.failures().stream().anyMatch(failure ->
                failure.code() == TransactionBindingFailureCode.VALIDATION_CONSUMPTION_AUTHORITY_INVALID));
    }

    @Test
    void resultEvidenceBindsProposalFreshnessPlanInputsAndTerminalResult() {
        TransactionValidationBinding binding = binding(
                transaction("test:tx/result", 5L),
                List.of(input("test:input/a", digest("input-a")))
        );
        AuthoritativeTransactionResultEvidence evidence = AuthoritativeTransactionResultEvidence.forBinding(
                binding,
                TransactionTerminalResult.APPLIED,
                List.of(new ResultingInventoryFreshnessEvidence(
                        "test:result/inventory",
                        freshness("after", 100L)
                ))
        );

        assertTrue(evidence.digestMatches());
        assertEquals(binding.transactionId(), evidence.transactionId());
        assertEquals(binding.proposalIdentity(), evidence.proposalIdentity());
        assertEquals(binding.inventoryFreshnessIdentity(), evidence.startingInventoryFreshnessIdentity());
        assertEquals(binding.validationPlanIdentity(), evidence.validationPlanIdentity());
        assertEquals(binding.validationInputIdentities(), evidence.validationInputIdentities());
        assertEquals(TransactionTerminalResult.APPLIED, evidence.terminalResult());
        assertTrue(EVALUATOR.validateResultEvidence(evidence, binding).successful());
    }

    @Test
    void resultEvidenceMismatchFailsTyped() {
        TransactionValidationBinding binding = binding(transaction("test:tx/result_mismatch", 5L), List.of());
        AuthoritativeTransactionResultEvidence evidence = new AuthoritativeTransactionResultEvidence(
                TransactionBindingSchema.CURRENT_VERSION,
                binding.transactionId(),
                binding.proposalIdentity(),
                binding.inventoryFreshnessIdentity(),
                TransactionValidationPlanIdentity.of(digest("different-plan")),
                TransactionTerminalResult.APPLIED,
                List.of(),
                binding.validationInputIdentities(),
                TransactionBindingValidation.zeroDigest()
        ).withCalculatedDigest();

        TransactionBindingValidationResult result = EVALUATOR.validateResultEvidence(evidence, binding);

        assertFalse(result.successful());
        assertTrue(contains(result, TransactionBindingFailureCode.VALIDATION_PLAN_IDENTITY_MISMATCH));
    }

    @Test
    void unsupportedSchemaFailsTyped() {
        TransactionValidationBinding binding = binding(transaction("test:tx/schema", 5L), List.of());
        TransactionValidationBindingCandidate candidate = new TransactionValidationBindingCandidate(
                99,
                Optional.of(binding.transactionId()),
                Optional.of(binding.proposalIdentity()),
                Optional.of(binding.inventoryFreshnessIdentity()),
                Optional.of(binding.validationPlanIdentity()),
                binding.validationInputIdentities()
        );

        TransactionBindingValidationResult result = EVALUATOR.validateBindingCandidate(
                candidate,
                binding.validationInputIdentities()
        );

        assertFalse(result.successful());
        assertTrue(contains(result, TransactionBindingFailureCode.UNSUPPORTED_SCHEMA));
    }

    @Test
    void sameTransactionIdAndSameProposalObservesExistingResult() {
        TransactionValidationBinding binding = binding(transaction("test:tx/duplicate", 5L), List.of());
        AuthoritativeTransactionResultEvidence evidence = AuthoritativeTransactionResultEvidence.forBinding(
                binding,
                TransactionTerminalResult.APPLIED,
                List.of()
        );

        TransactionDuplicateDecision decision = EVALUATOR.classifySubmission(
                binding.transactionId(),
                binding.proposalIdentity(),
                Optional.of(evidence)
        );

        assertTrue(decision.successful());
        assertEquals(TransactionDuplicateOutcome.DUPLICATE_OBSERVATION, decision.outcome());
        assertEquals(Optional.of(evidence), decision.existingEvidence());
    }

    @Test
    void sameTransactionIdAndDifferentProposalConflicts() {
        TransactionValidationBinding existingBinding = binding(transaction("test:tx/conflict", 5L), List.of());
        AuthoritativeTransactionResultEvidence evidence = AuthoritativeTransactionResultEvidence.forBinding(
                existingBinding,
                TransactionTerminalResult.APPLIED,
                List.of()
        );
        TransactionProposalIdentity differentProposal = TransactionProposalIdentity.from(
                transaction("test:tx/conflict", 6L)
        );

        TransactionDuplicateDecision decision = EVALUATOR.classifySubmission(
                existingBinding.transactionId(),
                differentProposal,
                Optional.of(evidence)
        );

        assertFalse(decision.successful());
        assertEquals(TransactionDuplicateOutcome.TRANSACTION_IDENTITY_CONFLICT, decision.outcome());
        assertTrue(decision.failures().stream().anyMatch(failure ->
                failure.code() == TransactionBindingFailureCode.TRANSACTION_IDENTITY_CONFLICT));
    }

    @Test
    void identicalExplicitInputsProduceIdenticalValidationDecision() {
        TransactionValidationBinding binding = binding(
                transaction("test:tx/reproducible", 5L),
                List.of(input("test:input/a", digest("input-a")))
        );

        assertEquals(
                EVALUATOR.validateBinding(binding, binding.validationInputIdentities()),
                EVALUATOR.validateBinding(binding, binding.validationInputIdentities())
        );
    }

    @Test
    void replayMetadataDoesNotCarryRuntimeAuthority() {
        TransactionValidationBinding binding = binding(transaction("test:tx/replay", 5L), List.of());
        AuthoritativeTransactionResultEvidence evidence = AuthoritativeTransactionResultEvidence.forBinding(
                binding,
                TransactionTerminalResult.APPLIED,
                List.of()
        );
        TransactionReplayValidationMetadata metadata = new TransactionReplayValidationMetadata(
                binding.proposalIdentity(),
                binding.inventoryFreshnessIdentity(),
                binding.validationPlanIdentity(),
                binding.validationInputIdentities(),
                evidence
        );

        assertEquals(evidence, metadata.resultEvidence());
    }

    private static EconomicTransaction transaction(String id, long quantity) {
        InventoryChange change = InventoryChange.add(
                InventoryTestFixtures.BEEF_INVENTORY,
                new InventoryEntry(InventoryTestFixtures.BEEF, quantity, UnitOfMeasure.POUND)
        );
        return EconomicTransaction.builder()
                .id(TransactionId.of(id))
                .type(TransactionType.INVENTORY_ADD)
                .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY)
                .goodId(InventoryTestFixtures.BEEF)
                .quantity(quantity)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .simulationTick(42L)
                .inventoryChange(change)
                .build();
    }

    private static TransactionValidationBinding binding(
            EconomicTransaction transaction,
            List<ValidationInputIdentity> validationInputIdentities
    ) {
        TransactionValidationPlan plan = TransactionValidationPlan.fromInventoryChanges(
                transaction.inventoryChangePlan(),
                List.of()
        );
        return TransactionValidationBinding.of(
                transaction.id(),
                TransactionProposalIdentity.from(transaction),
                freshness("before", transaction.simulationTick()),
                plan.identity(),
                validationInputIdentities
        );
    }

    private static InventoryFreshnessIdentity freshness(String source, long tick) {
        return InventoryFreshnessIdentity.fromComponents(
                "butchercraft:inventory",
                List.of(InventoryFreshnessComponent.of(
                        "test:inventory/" + source,
                        "test:inventory_runtime/" + source,
                        digest("freshness-" + source),
                        tick
                ))
        );
    }

    private static ValidationInputIdentity input(String identity, String contentDigest) {
        return ValidationInputIdentity.of(identity, contentDigest);
    }

    private static String digest(String value) {
        return TransactionBindingCanonicalDigest.create("test:digest")
                .add(value)
                .finish();
    }

    private static boolean contains(
            TransactionBindingValidationResult result,
            TransactionBindingFailureCode code
    ) {
        return result.failures().stream().anyMatch(failure -> failure.code() == code);
    }
}
