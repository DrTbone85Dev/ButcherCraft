package com.butchercraft.engine;

import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.result.OperationWarning;
import com.butchercraft.engine.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationResultTest {
    @Test
    void validSuccessContainsProposedOutputWithoutFailureReason() {
        Product input = EngineTestFixtures.rawProduct();
        Product output = input.withQuality(input.quality().adjustedByClamped(10));

        OperationResult result = OperationResult.success(
                input,
                TransactionState.PREPARED,
                Optional.of(output),
                Optional.empty(),
                List.of(),
                List.of()
        );

        assertTrue(result.succeeded());
        assertTrue(result.failureReason().isEmpty());
        assertEquals(Optional.of(output), result.proposedOutput());
        assertEquals(Optional.empty(), result.committedOutput());
        assertEquals(Optional.of(output.quality()), result.resultingQuality());
    }

    @Test
    void validFailureContainsReasonAndNoCommittedOutput() {
        Product input = EngineTestFixtures.rawProduct();
        FailureReason reason = new FailureReason("test_failure", "Test failure");

        OperationResult result = OperationResult.failure(
                input,
                TransactionState.REJECTED,
                reason,
                Optional.empty(),
                List.of(),
                List.of()
        );

        assertFalse(result.succeeded());
        assertEquals(Optional.of(reason), result.failureReason());
        assertEquals(Optional.empty(), result.committedOutput());
    }

    @Test
    void impossibleMixedSuccessStatesAreRejected() {
        Product input = EngineTestFixtures.rawProduct();

        assertThrows(IllegalArgumentException.class, () -> OperationResult.success(
                input,
                TransactionState.FAILED,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> OperationResult.success(
                input,
                TransactionState.PREPARED,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> OperationResult.success(
                input,
                TransactionState.VALIDATED,
                Optional.empty(),
                Optional.of(input),
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> OperationResult.success(
                input,
                TransactionState.COMMITTED,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of()
        ));
    }

    @Test
    void warningsAndAppliedModifiersAreRetained() {
        Product input = EngineTestFixtures.rawProduct();
        Product output = input.withQuality(input.quality().adjustedByClamped(10));
        ProcessingModifier modifier = EngineTestFixtures.warningModifier("warm_product", 1);
        OperationWarning warning = new OperationWarning(modifier.id(), modifier.reason());

        OperationResult result = OperationResult.success(
                input,
                TransactionState.PREPARED,
                Optional.of(output),
                Optional.empty(),
                List.of(modifier),
                List.of(warning)
        );

        assertEquals(List.of(modifier), result.appliedModifiers());
        assertEquals(List.of(warning), result.warnings());
    }
}
