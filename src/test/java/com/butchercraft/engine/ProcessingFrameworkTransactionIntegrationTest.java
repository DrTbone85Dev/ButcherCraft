package com.butchercraft.engine;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.transaction.ProcessingTransaction;
import com.butchercraft.engine.transaction.TransactionState;
import com.butchercraft.engine.validation.ValidationRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingFrameworkTransactionIntegrationTest {
    @Test
    void prepareAndCommitThroughContext() {
        ProcessingTransaction transaction = ProcessingTransaction.create(
                EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of())
        );

        OperationResult prepared = transaction.prepare();
        OperationResult committed = transaction.commit();

        assertTrue(prepared.succeeded());
        assertTrue(committed.succeeded());
        assertEquals(TransactionState.COMMITTED, transaction.state());
        assertEquals(prepared.proposedOutput(), committed.committedOutput());
    }

    @Test
    void prepareAndCancelPreservesInputAndWarnings() {
        ProcessingTransaction transaction = ProcessingTransaction.create(
                EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of())
        );

        OperationResult prepared = transaction.prepare();
        OperationResult cancelled = transaction.cancel();

        assertTrue(prepared.succeeded());
        assertFalse(cancelled.succeeded());
        assertEquals(TransactionState.CANCELLED, transaction.state());
        assertEquals(prepared.proposedOutput(), cancelled.proposedOutput());
        assertEquals(List.of("Fixture values are not final balance"), prepared.warnings().stream().map(warning -> warning.message()).toList());
    }

    @Test
    void doubleCommitCommitAfterRejectionAndCommitAfterCancellationAreSafe() {
        ProcessingTransaction committed = ProcessingTransaction.create(
                EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of())
        );
        committed.prepare();
        assertTrue(committed.commit().succeeded());
        assertFalse(committed.commit().succeeded());
        assertEquals(TransactionState.COMMITTED, committed.state());

        ProcessingTransaction rejected = ProcessingTransaction.create(
                EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(50), List.of())
        );
        assertFalse(rejected.prepare().succeeded());
        assertFalse(rejected.commit().succeeded());
        assertEquals(TransactionState.REJECTED, rejected.state());

        ProcessingTransaction cancelled = ProcessingTransaction.create(
                EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of())
        );
        cancelled.cancel();
        assertFalse(cancelled.commit().succeeded());
        assertEquals(TransactionState.CANCELLED, cancelled.state());
    }

    @Test
    void zeroOutputCannotBeCommittedWhenForbidden() {
        ProcessingOperation operation = new ProcessingOperation(
                EngineId.of("fixture:zero_output"),
                "Zero Output",
                EngineTestFixtures.BEEF_TRIM,
                Optional.empty(),
                ProcessingState.RAW,
                EngineTestFixtures.GROUND_BEEF,
                ProcessingState.PREPARED,
                ProcessingDuration.milliseconds(100),
                new YieldRatio(1, 3),
                0,
                List.of(
                        ValidationRules.requiredProductType(),
                        ValidationRules.requiredProcessingState(),
                        ValidationRules.zeroOutputNotPermitted()
                ),
                List.of(),
                false
        );
        ProcessingTransaction transaction = ProcessingTransaction.create(ProcessingContext.neutral(EngineTestFixtures.beefTrim(1), operation));

        OperationResult prepared = transaction.prepare();

        assertFalse(prepared.succeeded());
        assertEquals("zero_output_not_permitted", prepared.failureReason().orElseThrow().code());
        assertFalse(transaction.commit().succeeded());
        assertTrue(transaction.committedOutput().isEmpty());
    }

    @Test
    void evaluationFailureDoesNotMutateInput() {
        ProcessingOperation operation = new ProcessingOperation(
                EngineId.of("fixture:overflow_output"),
                "Overflow Output",
                EngineTestFixtures.BEEF_TRIM,
                Optional.empty(),
                ProcessingState.RAW,
                EngineTestFixtures.GROUND_BEEF,
                ProcessingState.PREPARED,
                ProcessingDuration.milliseconds(100),
                new YieldRatio(2, 1),
                0,
                List.of(ValidationRules.requiredProductType(), ValidationRules.requiredProcessingState()),
                List.of(),
                true
        );
        var input = EngineTestFixtures.beefTrim(Long.MAX_VALUE);
        ProcessingTransaction transaction = ProcessingTransaction.create(ProcessingContext.neutral(input, operation));

        OperationResult result = transaction.prepare();

        assertFalse(result.succeeded());
        assertEquals(TransactionState.FAILED, transaction.state());
        assertEquals(input, transaction.input());
        assertTrue(transaction.committedOutput().isEmpty());
    }

    @Test
    void sameInputAndContextProduceSameProposal() {
        ProcessingContext context = EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of());

        OperationResult first = ProcessingTransaction.create(context).prepare();
        OperationResult second = ProcessingTransaction.create(context).prepare();

        assertEquals(first.proposedOutput(), second.proposedOutput());
    }
}
