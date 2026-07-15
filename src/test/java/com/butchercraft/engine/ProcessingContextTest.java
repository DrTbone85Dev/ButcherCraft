package com.butchercraft.engine;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.evaluation.ProcessingEvaluator;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.result.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingContextTest {
    @Test
    void validConstructionStoresExplicitInputs() {
        ProcessingOperation operation = EngineTestFixtures.grindBeefOperation();
        ProcessingContext context = EngineTestFixtures.grindContext(
                EngineTestFixtures.beefTrim(1_000),
                List.of(EngineTestFixtures.yieldModifier("yield_bonus", 100, 5))
        );

        assertEquals(operation.id(), context.operation().id());
        assertEquals(800, context.cleanliness().value());
        assertEquals(600, context.operatorSkill().value());
        assertEquals(900, context.equipmentCondition().value());
        assertEquals(List.of("yield_bonus"), context.additionalModifiers().stream()
                .map(modifier -> modifier.id().value())
                .toList());
    }

    @Test
    void invalidBoundedFactorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProcessingFactor.of(-1));
        assertThrows(IllegalArgumentException.class, () -> ProcessingFactor.of(1001));
    }

    @Test
    void contextOperationMismatchIsRejectedByEvaluator() {
        ProcessingContext context = EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of());

        OperationResult result = ProcessingEvaluator.prepare(EngineTestFixtures.basicOperation(List.of()), context);

        assertFalse(result.succeeded());
        assertEquals("context_operation_mismatch", result.failureReason().orElseThrow().code());
    }

    @Test
    void modifiersAreCopiedAndUnmodifiable() {
        List<ProcessingModifier> modifiers = new ArrayList<>();
        modifiers.add(EngineTestFixtures.yieldModifier("yield_bonus", 100, 5));
        ProcessingContext context = EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), modifiers);
        modifiers.clear();

        assertEquals(1, context.additionalModifiers().size());
        assertThrows(UnsupportedOperationException.class, () -> context.additionalModifiers().add(EngineTestFixtures.warningModifier("late", 1)));
    }

    @Test
    void neutralContextUsesDocumentedDefaults() {
        ProcessingContext context = ProcessingContext.neutral(EngineTestFixtures.beefTrim(1_000), EngineTestFixtures.grindBeefOperation());

        assertEquals(1000, context.cleanliness().value());
        assertEquals(500, context.operatorSkill().value());
        assertEquals(1000, context.equipmentCondition().value());
        assertEquals(List.of(), context.additionalModifiers());
    }
}
