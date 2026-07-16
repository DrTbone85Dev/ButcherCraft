package com.butchercraft.engine;

import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.validation.ValidationRule;
import com.butchercraft.engine.validation.ValidationRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingOperationTest {
    @Test
    void validConstructionStoresTransformationDefinition() {
        ProcessingOperation operation = EngineTestFixtures.grindBeefOperation();

        assertEquals(EngineTestFixtures.GRIND_BEEF, operation.id());
        assertEquals("Grind Beef", operation.name());
        assertEquals(EngineTestFixtures.BEEF_TRIM, operation.requiredProductType());
        assertEquals(Optional.of(ProductCategory.BEEF), operation.requiredSourceCategory());
        assertEquals(ProcessingState.RAW, operation.requiredProcessingState());
        assertEquals(EngineTestFixtures.GROUND_BEEF, operation.outputProductType());
        assertEquals(ProcessingState.PREPARED, operation.outputProcessingState());
        assertEquals(3_000, operation.baseDuration().milliseconds());
        assertEquals(new YieldRatio(9, 10), operation.baseYield());
        assertEquals(1, operation.outputs().size());
    }

    @Test
    void invalidIdentifierDurationYieldAndMissingDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EngineId.of("Bad:Id"));
        assertThrows(IllegalArgumentException.class, () -> ProcessingDuration.milliseconds(0));
        assertThrows(IllegalArgumentException.class, () -> new YieldRatio(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new YieldRatio(1, 0));

        ProcessingOperation operation = EngineTestFixtures.grindBeefOperation();
        assertThrows(NullPointerException.class, () -> new ProcessingOperation(
                null,
                operation.name(),
                operation.requiredProductType(),
                operation.requiredSourceCategory(),
                operation.requiredProcessingState(),
                operation.outputProductType(),
                operation.outputProcessingState(),
                operation.baseDuration(),
                operation.baseYield(),
                operation.baseQualityDelta(),
                operation.validationRules(),
                operation.modifiers(),
                operation.zeroOutputPermitted()
        ));
    }

    @Test
    void copiedListsPreserveImmutabilityAndRuleOrder() {
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(ValidationRules.requiredProductType());
        rules.add(ValidationRules.requiredProcessingState());
        List<ProcessingModifier> modifiers = new ArrayList<>();
        modifiers.add(new ProcessingModifier(EngineId.of("fixture/static_quality"), "Static", ModifierCategory.QUALITY, 3, 1));

        ProcessingOperation operation = new ProcessingOperation(
                EngineId.of("fixture:operation"),
                "Fixture Operation",
                EngineTestFixtures.BEEF_TRIM,
                Optional.empty(),
                ProcessingState.RAW,
                EngineTestFixtures.GROUND_BEEF,
                ProcessingState.PREPARED,
                ProcessingDuration.milliseconds(100),
                YieldRatio.identity(),
                0,
                rules,
                modifiers,
                false
        );
        rules.clear();
        modifiers.clear();

        assertEquals(List.of("validation/required_product_type", "validation/required_processing_state"), operation.validationRules().stream()
                .map(rule -> rule.id().value())
                .toList());
        assertEquals(List.of("fixture/static_quality"), operation.modifiers().stream()
                .map(modifier -> modifier.id().value())
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> operation.validationRules().add(ValidationRules.zeroOutputNotPermitted()));
        assertThrows(UnsupportedOperationException.class, () -> operation.modifiers().add(EngineTestFixtures.qualityModifier("late", 1, 1)));
        assertThrows(UnsupportedOperationException.class, () -> operation.outputs().clear());
    }

    @Test
    void staticModifiersAreRetained() {
        ProcessingOperation operation = EngineTestFixtures.grindBeefOperation();

        assertEquals(List.of("fixture/grind_quality_adjustment"), operation.modifiers().stream()
                .map(modifier -> modifier.id().value())
                .toList());
    }
}
