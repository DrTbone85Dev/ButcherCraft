package com.butchercraft.engine;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.evaluation.ProcessingEvaluator;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.validation.ValidationResult;
import com.butchercraft.engine.validation.ValidationRules;
import com.butchercraft.engine.validation.ValidationSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationRuleTest {
    @Test
    void productTypeRuleAcceptsAndRejects() {
        ProcessingContext accepted = EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of());
        Product wrong = new Product(EngineId.of("butchercraft:pork_trim"), ProductCategory.PORK, ProcessingState.RAW, ProductQuantity.grams(1_000), ProductQuality.ofScore(650));

        assertTrue(ValidationRules.requiredProductType().evaluate(accepted).isAccepted());
        assertEquals("wrong_product_type", ValidationRules.requiredProductType()
                .evaluate(EngineTestFixtures.grindContext(wrong, List.of()))
                .rejectionReason()
                .orElseThrow()
                .code());
    }

    @Test
    void sourceCategoryRuleAcceptsOptionalMatchAndRejectsMismatch() {
        ProcessingContext accepted = EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of());
        Product wrongCategory = new Product(EngineTestFixtures.BEEF_TRIM, ProductCategory.PORK, ProcessingState.RAW, ProductQuantity.grams(1_000), ProductQuality.ofScore(650));

        assertTrue(ValidationRules.requiredSourceCategory().evaluate(accepted).isAccepted());
        assertEquals("wrong_source_category", ValidationRules.requiredSourceCategory()
                .evaluate(EngineTestFixtures.grindContext(wrongCategory, List.of()))
                .rejectionReason()
                .orElseThrow()
                .code());
    }

    @Test
    void stateRuleAcceptsAndRejects() {
        Product prepared = new Product(EngineTestFixtures.BEEF_TRIM, ProductCategory.BEEF, ProcessingState.PREPARED, ProductQuantity.grams(1_000), ProductQuality.ofScore(650));

        assertTrue(ValidationRules.requiredProcessingState().evaluate(EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of())).isAccepted());
        assertEquals("wrong_processing_state", ValidationRules.requiredProcessingState()
                .evaluate(EngineTestFixtures.grindContext(prepared, List.of()))
                .rejectionReason()
                .orElseThrow()
                .code());
    }

    @Test
    void minimumRulesRejectBelowThresholds() {
        ProcessingContext tooSmall = EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(50), List.of());
        ProcessingContext dirty = new ProcessingContext(
                EngineTestFixtures.beefTrim(1_000),
                EngineTestFixtures.grindBeefOperation(),
                ProcessingFactor.of(599),
                ProcessingFactor.NEUTRAL_SKILL,
                ProcessingFactor.IDEAL,
                List.of()
        );
        ProcessingContext dull = new ProcessingContext(
                EngineTestFixtures.beefTrim(1_000),
                EngineTestFixtures.grindBeefOperation(),
                ProcessingFactor.IDEAL,
                ProcessingFactor.NEUTRAL_SKILL,
                ProcessingFactor.of(499),
                List.of()
        );

        assertEquals("minimum_quantity_not_met", ValidationRules.minimumQuantity(ProductQuantity.grams(100)).evaluate(tooSmall).rejectionReason().orElseThrow().code());
        assertEquals("minimum_cleanliness_not_met", ValidationRules.minimumCleanliness(ProcessingFactor.of(600)).evaluate(dirty).rejectionReason().orElseThrow().code());
        assertEquals("minimum_equipment_condition_not_met", ValidationRules.minimumEquipmentCondition(ProcessingFactor.of(500)).evaluate(dull).rejectionReason().orElseThrow().code());
    }

    @Test
    void warningOnlyResultDoesNotReject() {
        ValidationResult result = ValidationRules.warning(EngineId.of("validation/test_warning"), "Test warning")
                .evaluate(EngineTestFixtures.grindContext(EngineTestFixtures.beefTrim(1_000), List.of()));

        assertTrue(result.isAccepted());
        assertTrue(result.warning().isPresent());
    }

    @Test
    void rejectionOrderIsDeterministicAndStopsAtFirstFailure() {
        Product wrongAndDirty = new Product(EngineId.of("butchercraft:pork_trim"), ProductCategory.PORK, ProcessingState.PREPARED, ProductQuantity.grams(50), ProductQuality.ofScore(650));
        ProcessingOperation operation = new ProcessingOperation(
                EngineId.of("fixture:ordered_validation"),
                "Ordered Validation",
                EngineTestFixtures.BEEF_TRIM,
                Optional.of(ProductCategory.BEEF),
                ProcessingState.RAW,
                EngineTestFixtures.GROUND_BEEF,
                ProcessingState.PREPARED,
                ProcessingDuration.milliseconds(100),
                YieldRatio.identity(),
                0,
                List.of(
                        ValidationRules.requiredProductType(),
                        ValidationRules.requiredProcessingState(),
                        ValidationRules.minimumQuantity(ProductQuantity.grams(100))
                ),
                List.of(),
                false
        );
        ProcessingContext context = ProcessingContext.neutral(wrongAndDirty, operation);

        ValidationSummary first = ProcessingEvaluator.validate(operation, context);
        ValidationSummary second = ProcessingEvaluator.validate(operation, context);

        assertFalse(first.accepted());
        assertEquals(first, second);
        assertEquals("wrong_product_type", first.rejectionReason().orElseThrow().code());
        assertEquals(1, first.results().size());
    }
}
