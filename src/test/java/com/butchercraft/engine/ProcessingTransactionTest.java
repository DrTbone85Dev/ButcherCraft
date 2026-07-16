package com.butchercraft.engine;

import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.transaction.ProcessingTransaction;
import com.butchercraft.engine.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingTransactionTest {
    @Test
    void normalLifecycleValidatesPreparesAndCommits() {
        ProcessingTransaction transaction = ProcessingTransaction.create(
                EngineTestFixtures.rawProduct(),
                EngineTestFixtures.basicOperation(List.of(EngineTestFixtures.qualityModifier("sharp_blade", 25, 1)))
        );

        assertEquals(TransactionState.CREATED, transaction.state());
        assertTrue(transaction.validate().succeeded());
        assertEquals(TransactionState.VALIDATED, transaction.state());

        OperationResult prepared = transaction.prepare();
        assertTrue(prepared.succeeded());
        assertEquals(TransactionState.PREPARED, transaction.state());
        assertEquals(500, prepared.resultingQuantity().orElseThrow().amount());
        assertEquals(700, prepared.resultingQuality().orElseThrow().score());

        OperationResult committed = transaction.commit();
        assertTrue(committed.succeeded());
        assertEquals(TransactionState.COMMITTED, transaction.state());
        assertEquals(prepared.proposedOutput(), committed.committedOutput());
    }

    @Test
    void rejectedInputDoesNotChangeInputProduct() {
        Product input = EngineTestFixtures.rawProduct();
        ProcessingOperation wrongOperation = new ProcessingOperation(
                EngineId.of("wrong_operation"),
                EngineId.of("other_product"),
                ProcessingState.RAW,
                EngineTestFixtures.PREPARED_BEEF,
                ProcessingState.PREPARED,
                20,
                YieldRatio.identity(),
                0,
                List.of()
        );

        ProcessingTransaction transaction = ProcessingTransaction.create(input, wrongOperation);
        OperationResult result = transaction.validate();

        assertFalse(result.succeeded());
        assertEquals(TransactionState.REJECTED, transaction.state());
        assertEquals(input, transaction.input());
        assertTrue(transaction.proposedOutput().isEmpty());
    }

    @Test
    void prepareDoesNotCommitOutput() {
        ProcessingTransaction transaction = ProcessingTransaction.create(
                EngineTestFixtures.rawProduct(),
                EngineTestFixtures.basicOperation(List.of())
        );

        OperationResult prepared = transaction.prepare();

        assertTrue(prepared.succeeded());
        assertTrue(transaction.proposedOutput().isPresent());
        assertTrue(transaction.committedOutput().isEmpty());
    }

    @Test
    void commitCanOnlyProduceOutputOnce() {
        ProcessingTransaction transaction = ProcessingTransaction.create(
                EngineTestFixtures.rawProduct(),
                EngineTestFixtures.basicOperation(List.of())
        );

        transaction.prepare();
        OperationResult firstCommit = transaction.commit();
        OperationResult secondCommit = transaction.commit();

        assertTrue(firstCommit.succeeded());
        assertFalse(secondCommit.succeeded());
        assertEquals(TransactionState.COMMITTED, transaction.state());
        assertEquals(firstCommit.committedOutput(), transaction.committedOutput());
    }

    @Test
    void cancellationBeforeCommitPreservesProposedOutputForInspection() {
        ProcessingTransaction transaction = ProcessingTransaction.create(
                EngineTestFixtures.rawProduct(),
                EngineTestFixtures.basicOperation(List.of())
        );

        OperationResult prepared = transaction.prepare();
        OperationResult cancelled = transaction.cancel();

        assertTrue(prepared.succeeded());
        assertFalse(cancelled.succeeded());
        assertEquals(TransactionState.CANCELLED, transaction.state());
        assertEquals(prepared.proposedOutput(), cancelled.proposedOutput());
        assertTrue(transaction.committedOutput().isEmpty());
    }

    @Test
    void commitAfterCancellationAndCancelAfterCommitAreRejected() {
        ProcessingTransaction cancelled = ProcessingTransaction.create(
                EngineTestFixtures.rawProduct(),
                EngineTestFixtures.basicOperation(List.of())
        );
        cancelled.cancel();

        assertFalse(cancelled.commit().succeeded());
        assertEquals(TransactionState.CANCELLED, cancelled.state());

        ProcessingTransaction committed = ProcessingTransaction.create(
                EngineTestFixtures.rawProduct(),
                EngineTestFixtures.basicOperation(List.of())
        );
        committed.prepare();
        committed.commit();

        assertFalse(committed.cancel().succeeded());
        assertEquals(TransactionState.COMMITTED, committed.state());
        assertTrue(committed.committedOutput().isPresent());
    }

    @Test
    void illegalStateTransitionsAreRejectedWithoutLosingInput() {
        Product input = EngineTestFixtures.rawProduct();
        ProcessingTransaction transaction = ProcessingTransaction.create(input, EngineTestFixtures.basicOperation(List.of()));

        assertFalse(transaction.commit().succeeded());
        assertEquals(TransactionState.CREATED, transaction.state());

        transaction.prepare();

        assertFalse(transaction.validate().succeeded());
        assertFalse(transaction.prepare().succeeded());
        assertEquals(TransactionState.PREPARED, transaction.state());
        assertEquals(input, transaction.input());
    }

    @Test
    void deterministicOutputAndModifierOrderAreStable() {
        List<ProcessingModifier> modifiers = List.of(
                EngineTestFixtures.qualityModifier("z_bonus", 5, 10),
                EngineTestFixtures.qualityModifier("a_bonus", 5, 10),
                EngineTestFixtures.warningModifier("check_operator", 20)
        );
        Product input = EngineTestFixtures.rawProduct();

        ProcessingTransaction first = ProcessingTransaction.create(input, EngineTestFixtures.basicOperation(modifiers));
        ProcessingTransaction second = ProcessingTransaction.create(input, EngineTestFixtures.basicOperation(modifiers));

        OperationResult firstPrepared = first.prepare();
        OperationResult secondPrepared = second.prepare();

        assertEquals(firstPrepared.proposedOutput(), secondPrepared.proposedOutput());
        assertEquals(firstPrepared.appliedModifiers(), secondPrepared.appliedModifiers());
        assertEquals(List.of("a_bonus", "z_bonus", "check_operator"), firstPrepared.appliedModifiers().stream()
                .map(modifier -> modifier.id().value())
                .toList());
    }

    @Test
    void multiOutputCommitPreservesOrderedCommittedOutputs() {
        ProcessingTransaction transaction = ProcessingTransaction.create(
                EngineTestFixtures.forequarterContext(EngineTestFixtures.beefForequarter(100_000), false)
        );

        OperationResult prepared = transaction.prepare();
        OperationResult committed = transaction.commit();

        assertTrue(prepared.succeeded());
        assertTrue(committed.succeeded());
        assertEquals(8, committed.committedOutputs().size());
        assertEquals(prepared.proposedOutputs(), committed.committedOutputs());
        assertEquals(List.of(
                "butchercraft:beef_chuck",
                "butchercraft:beef_rib",
                "butchercraft:beef_brisket",
                "butchercraft:beef_plate",
                "butchercraft:beef_shank",
                "butchercraft:beef_trim",
                "butchercraft:beef_fat",
                "butchercraft:beef_bone"
        ), transaction.committedOutputs().stream().map(output -> output.typeId().value()).toList());
    }
}
