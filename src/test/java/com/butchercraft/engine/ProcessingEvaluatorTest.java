package com.butchercraft.engine;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.evaluation.ProcessingEvaluator;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.result.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingEvaluatorTest {
    @Test
    void validBeefTrimToGroundBeefPreparationCreatesProposedOutput() {
        ProcessingContext context = EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of());

        OperationResult result = ProcessingEvaluator.prepare(context.operation(), context);

        assertTrue(result.succeeded());
        Product output = result.proposedOutput().orElseThrow();
        assertEquals(EngineTestFixtures.GROUND_BEEF, output.typeId());
        assertEquals(ProductCategory.BEEF, output.sourceCategory());
        assertEquals(ProcessingState.PREPARED, output.processingState());
        assertEquals(ProductQuantity.grams(900), output.quantity());
        assertEquals(ProductQuality.ofScore(624), output.quality());
        assertTrue(result.committedOutput().isEmpty());
    }

    @Test
    void invalidProductStateAndCleanlinessReject() {
        Product wrongProduct = new Product(
                EngineId.of("butchercraft:pork_trim"),
                ProductCategory.fromId(EngineId.of("butchercraft:pork")),
                ProcessingState.RAW,
                ProductQuantity.grams(1_000),
                ProductQuality.ofScore(650)
        );
        Product wrongState = new Product(EngineTestFixtures.BEEF_TRIM, ProductCategory.BEEF, ProcessingState.PREPARED, ProductQuantity.grams(1_000), ProductQuality.ofScore(650));
        ProcessingContext dirty = new ProcessingContext(
                EngineTestFixtures.beefTrim(1_000),
                EngineTestFixtures.grindBeefOperation(),
                ProcessingFactor.of(500),
                ProcessingFactor.NEUTRAL_SKILL,
                ProcessingFactor.IDEAL,
                List.of()
        );

        assertEquals("wrong_product_type", ProcessingEvaluator.prepare(EngineTestFixtures.grindBeefOperation(), EngineTestFixtures.grindContext(wrongProduct, List.of())).failureReason().orElseThrow().code());
        assertEquals("wrong_processing_state", ProcessingEvaluator.prepare(EngineTestFixtures.grindBeefOperation(), EngineTestFixtures.grindContext(wrongState, List.of())).failureReason().orElseThrow().code());
        assertEquals("minimum_cleanliness_not_met", ProcessingEvaluator.prepare(dirty.operation(), dirty).failureReason().orElseThrow().code());
    }

    @Test
    void warningsAndContextModifiersAreRetained() {
        ProcessingContext context = EngineTestFixtures.grindContext(
                EngineTestFixtures.beefTrim(1_000),
                List.of(EngineTestFixtures.warningModifier("operator_note", 50))
        );

        OperationResult result = ProcessingEvaluator.prepare(context.operation(), context);

        assertTrue(result.succeeded());
        assertEquals(List.of("fixture/grind_quality_adjustment", "operator_note", "context/cleanliness_factor", "context/equipment_condition", "context/operator_skill"), result.appliedModifiers().stream()
                .map(modifier -> modifier.id().value())
                .toList());
        assertEquals(List.of("Fixture values are not final balance", "Warning fixture operator_note"), result.warnings().stream()
                .map(warning -> warning.message())
                .toList());
    }

    @Test
    void contextYieldModifiersApplyDeterministicallyWithoutMutatingInput() {
        Product input = EngineTestFixtures.beefTrim(1_000);
        ProcessingContext context = EngineTestFixtures.grindContext(input, List.of(EngineTestFixtures.yieldModifier("yield_bonus", 1_000, 5)));

        OperationResult first = ProcessingEvaluator.prepare(context.operation(), context);
        OperationResult second = ProcessingEvaluator.prepare(context.operation(), context);

        assertEquals(first, second);
        assertEquals(ProductQuantity.grams(1_000), first.resultingQuantity().orElseThrow());
        assertEquals(ProductQuantity.grams(1_000), input.quantity());
        assertFalse(first.committedOutput().isPresent());
    }

    @Test
    void multiOutputForequarterUsesOrderedExactRatios() {
        ProcessingContext context = EngineTestFixtures.forequarterContext(
                EngineTestFixtures.beefForequarter(100_000),
                false
        );

        OperationResult result = ProcessingEvaluator.prepare(context.operation(), context);

        assertTrue(result.succeeded(), result.failureReason().map(reason -> reason.message()).orElse(""));
        assertEquals(List.of(
                "butchercraft:beef_chuck",
                "butchercraft:beef_rib",
                "butchercraft:beef_brisket",
                "butchercraft:beef_plate",
                "butchercraft:beef_shank",
                "butchercraft:beef_trim",
                "butchercraft:beef_fat",
                "butchercraft:beef_bone"
        ), result.proposedOutputs().stream().map(output -> output.typeId().value()).toList());
        assertEquals(List.of(30_000L, 10_000L, 10_000L, 10_000L, 5_000L, 15_000L, 5_000L, 10_000L),
                result.proposedOutputs().stream().map(output -> output.quantity().amount()).toList());
        assertEquals(95_000L, result.proposedOutputs().stream().mapToLong(output -> output.quantity().amount()).sum());
    }

    @Test
    void multiOutputRoundingUsesLargestRemainderAndOutputOrderTieBreaks() {
        ProcessingContext context = EngineTestFixtures.forequarterContext(
                EngineTestFixtures.beefForequarter(11),
                true
        );

        OperationResult result = ProcessingEvaluator.prepare(context.operation(), context);

        assertTrue(result.succeeded(), result.failureReason().map(reason -> reason.message()).orElse(""));
        assertEquals(List.of(3L, 1L, 1L, 1L, 1L, 2L, 0L, 1L),
                result.proposedOutputs().stream().map(output -> output.quantity().amount()).toList());
        assertEquals(10L, result.proposedOutputs().stream().mapToLong(output -> output.quantity().amount()).sum());
    }
}
