package com.butchercraft.world.transaction;

import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import com.butchercraft.world.transaction.binding.TransactionProposalIdentity;
import com.butchercraft.world.transaction.binding.TransactionTerminalResult;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionLiveBindingIntegrationTest {
    @Test
    void managerAppliesValidTransactionWithBoundResultEvidence() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);
        EconomicTransaction transaction = TransactionTestFixtures.beefTransaction(
                "test:bound_add", TransactionType.INVENTORY_ADD, 5L, 26L
        );

        TransactionResult result = manager.submit(transaction);

        assertTrue(result.success());
        assertEquals(TransactionTerminalResult.APPLIED, result.terminalResult());
        assertTrue(result.resultEvidence().isPresent());
        var evidence = result.resultEvidence().orElseThrow();
        assertEquals(transaction.id(), evidence.transactionId());
        assertEquals(TransactionProposalIdentity.from(transaction), evidence.proposalIdentity());
        assertEquals(TransactionTerminalResult.APPLIED, evidence.terminalResult());
        assertTrue(evidence.digestMatches());
        assertEquals(evidence, manager.resultEvidenceFor(transaction.id()).orElseThrow());
        assertEquals(25L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
    }

    @Test
    void proposalIdentityIsStableAndChangesWithMaterialProposalContent() {
        EconomicTransaction first = TransactionTestFixtures.beefTransaction(
                "test:proposal", TransactionType.INVENTORY_ADD, 5L, 26L
        );
        EconomicTransaction same = EconomicTransaction.builder()
                .id(first.id())
                .type(first.type())
                .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY)
                .goodId(first.goodId())
                .quantity(first.quantity())
                .unitOfMeasure(first.unitOfMeasure())
                .simulationTick(first.simulationTick())
                .build();
        EconomicTransaction changed = EconomicTransaction.builder()
                .id(first.id())
                .type(first.type())
                .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY)
                .goodId(first.goodId())
                .quantity(6L)
                .unitOfMeasure(first.unitOfMeasure())
                .simulationTick(first.simulationTick())
                .build();

        assertEquals(TransactionProposalIdentity.from(first), TransactionProposalIdentity.from(same));
        assertFalse(TransactionProposalIdentity.from(first).equals(TransactionProposalIdentity.from(changed)));
    }

    @Test
    void duplicateAndConflictSubmissionsAreIdentityBoundAndDoNotDoubleApply() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);
        EconomicTransaction transaction = TransactionTestFixtures.beefTransaction(
                "test:duplicate", TransactionType.INVENTORY_ADD, 1L, 26L
        );

        TransactionResult first = manager.submit(transaction);
        TransactionResult duplicate = manager.submit(transaction);
        EconomicTransaction conflict = TransactionTestFixtures.beefTransaction(
                "test:duplicate", TransactionType.INVENTORY_ADD, 2L, 26L
        );
        TransactionResult conflictResult = manager.submit(conflict);

        assertTrue(first.success());
        assertTrue(duplicate.success());
        assertEquals(TransactionTerminalResult.DUPLICATE_OBSERVATION, duplicate.terminalResult());
        assertFalse(conflictResult.success());
        assertEquals(TransactionFailureCode.TRANSACTION_IDENTITY_CONFLICT, conflictResult.failureCode().orElseThrow());
        assertEquals(TransactionTerminalResult.CONFLICT, conflictResult.terminalResult());
        assertEquals(21L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
        assertEquals(1, manager.size());
    }

    @Test
    void concurrentDuplicateSubmissionsApplyAtMostOnce() throws Exception {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);
        EconomicTransaction transaction = TransactionTestFixtures.beefTransaction(
                "test:concurrent_duplicate", TransactionType.INVENTORY_ADD, 1L, 26L
        );
        List<Callable<TransactionResult>> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            tasks.add(() -> manager.submit(transaction));
        }

        try (var executor = Executors.newFixedThreadPool(4)) {
            List<TransactionResult> results = new ArrayList<>();
            for (var future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }

            assertEquals(1L, results.stream()
                    .filter(result -> result.terminalResult() == TransactionTerminalResult.APPLIED)
                    .count());
            assertEquals(7L, results.stream()
                    .filter(result -> result.terminalResult() == TransactionTerminalResult.DUPLICATE_OBSERVATION)
                    .count());
        }
        assertEquals(21L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
    }

    @Test
    void staleFreshnessFailsBeforeInventoryMutation() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionValidator validator = new TransactionValidator(inventory);
        TransactionExecutor executor = new TransactionExecutor(inventory);
        EconomicTransaction original = TransactionTestFixtures.beefTransaction(
                "test:stale", TransactionType.INVENTORY_ADD, 5L, 26L
        );
        TransactionValidation validation = validator.validateForSubmission(original);
        LiveTransactionValidation liveValidation = LiveTransactionValidation.issue(validation);

        TransactionManager manager = new TransactionManager(inventory);
        assertTrue(manager.submit(TransactionTestFixtures.beefTransaction(
                "test:intervening", TransactionType.INVENTORY_ADD, 1L, 26L
        )).success());

        TransactionResult stale = executor.execute(original.withStatus(TransactionStatus.VALIDATED), liveValidation);

        assertFalse(stale.success());
        assertEquals(TransactionFailureCode.INVENTORY_FRESHNESS_MISMATCH, stale.failureCode().orElseThrow());
        assertEquals(21L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
    }

    @Test
    void bindableValidationFailurePublishesRejectedEvidenceWithoutInventoryMutation() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);
        EconomicTransaction removal = TransactionTestFixtures.beefTransaction(
                "test:rejected_evidence", TransactionType.INVENTORY_REMOVE, 100L, 26L
        );

        TransactionResult result = manager.submit(removal);

        assertFalse(result.success());
        assertEquals(TransactionFailureCode.INSUFFICIENT_INVENTORY, result.failureCode().orElseThrow());
        assertEquals(TransactionTerminalResult.REJECTED, result.terminalResult());
        assertTrue(result.resultEvidence().isPresent());
        assertEquals(TransactionTerminalResult.REJECTED, result.resultEvidence().orElseThrow().terminalResult());
        assertEquals(20L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
    }

    @Test
    void changedValidationPlanFailsBeforeInventoryMutation() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionValidator validator = new TransactionValidator(inventory);
        TransactionExecutor executor = new TransactionExecutor(inventory);
        EconomicTransaction transaction = TransactionTestFixtures.beefTransaction(
                "test:plan_mismatch", TransactionType.INVENTORY_ADD, 5L, 26L
        );
        TransactionValidation validation = validator.validateForSubmission(transaction);
        TransactionValidation tampered = TransactionValidation.acceptedBound(
                validation.transactionId(),
                List.of(InventoryChange.add(
                        InventoryTestFixtures.BEEF_INVENTORY,
                        new InventoryEntry(InventoryTestFixtures.BEEF, 6L, UnitOfMeasure.POUND)
                )),
                validation.proposalIdentity().orElseThrow(),
                validation.inventoryFreshnessIdentity().orElseThrow(),
                validation.validationPlan().orElseThrow(),
                validation.binding().orElseThrow()
        );

        TransactionResult result = executor.execute(
                transaction.withStatus(TransactionStatus.VALIDATED),
                LiveTransactionValidation.issue(tampered)
        );

        assertFalse(result.success());
        assertEquals(TransactionFailureCode.VALIDATION_PLAN_MISMATCH, result.failureCode().orElseThrow());
        assertEquals(20L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
    }

    @Test
    void acceptedValidationPlanIsImmutableAndPublicExecutorDoesNotGrantMutationAuthority() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionValidator validator = new TransactionValidator(inventory);
        TransactionExecutor executor = new TransactionExecutor(inventory);
        EconomicTransaction transaction = TransactionTestFixtures.beefTransaction(
                "test:no_authority", TransactionType.INVENTORY_ADD, 5L, 26L
        );
        TransactionValidation validation = validator.validateForSubmission(transaction);

        assertThrows(UnsupportedOperationException.class,
                () -> validation.validationPlan().orElseThrow().steps().add(
                        validation.validationPlan().orElseThrow().steps().getFirst()
                ));
        TransactionResult result = executor.execute(transaction.withStatus(TransactionStatus.VALIDATED), validation);

        assertFalse(result.success());
        assertEquals(TransactionFailureCode.APPLICATION_WITHOUT_VALIDATION, result.failureCode().orElseThrow());
        assertEquals(20L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
    }

    @Test
    void persistedLegacyHistoryDoesNotInventLiveResultEvidenceForDuplicateObservation() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);
        EconomicTransaction transaction = TransactionTestFixtures.beefTransaction(
                "test:legacy_duplicate", TransactionType.INVENTORY_ADD, 1L, 26L
        );
        assertTrue(manager.submit(transaction).success());
        TransactionStorage storage = new TransactionStorage(Path.of("unused.json"), inventory);
        TransactionManager loaded = storage.deserialize(storage.serialize(manager));

        TransactionResult duplicate = loaded.submit(transaction);

        assertFalse(duplicate.success());
        assertEquals(TransactionFailureCode.PERSISTENCE_COMPATIBILITY_FAILURE, duplicate.failureCode().orElseThrow());
        assertTrue(loaded.resultEvidenceFor(transaction.id()).isEmpty());
    }
}
