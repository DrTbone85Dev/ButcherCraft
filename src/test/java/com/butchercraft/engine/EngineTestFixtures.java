package com.butchercraft.engine;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.validation.ValidationRules;

import java.util.List;
import java.util.Optional;

final class EngineTestFixtures {
    static final EngineId RAW_BEEF = EngineId.of("raw_beef");
    static final EngineId PREPARED_BEEF = EngineId.of("prepared_beef");
    static final EngineId BASIC_OPERATION = EngineId.of("basic_prepare");
    static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");
    static final EngineId GRIND_BEEF = EngineId.of("butchercraft:grind_beef");

    private EngineTestFixtures() {
    }

    static Product rawProduct() {
        return new Product(
                RAW_BEEF,
                ProductCategory.BEEF,
                ProcessingState.RAW,
                ProductQuantity.grams(1_000),
                ProductQuality.ofScore(650)
        );
    }

    static ProcessingOperation basicOperation(List<ProcessingModifier> modifiers) {
        return new ProcessingOperation(
                BASIC_OPERATION,
                "Basic Prepare",
                RAW_BEEF,
                Optional.empty(),
                ProcessingState.RAW,
                PREPARED_BEEF,
                ProcessingState.PREPARED,
                ProcessingDuration.milliseconds(1_000),
                new YieldRatio(1, 2),
                25,
                List.of(
                        ValidationRules.requiredProductType(),
                        ValidationRules.requiredProcessingState(),
                        ValidationRules.zeroOutputNotPermitted()
                ),
                modifiers,
                false
        );
    }

    static Product beefTrim(long grams) {
        return new Product(
                BEEF_TRIM,
                ProductCategory.BEEF,
                ProcessingState.RAW,
                ProductQuantity.grams(grams),
                ProductQuality.ofScore(650)
        );
    }

    static ProcessingOperation grindBeefOperation() {
        return new ProcessingOperation(
                GRIND_BEEF,
                "Grind Beef",
                BEEF_TRIM,
                Optional.of(ProductCategory.BEEF),
                ProcessingState.RAW,
                GROUND_BEEF,
                ProcessingState.PREPARED,
                ProcessingDuration.milliseconds(3_000),
                new YieldRatio(9, 10),
                -5,
                List.of(
                        ValidationRules.requiredProductType(),
                        ValidationRules.requiredSourceCategory(),
                        ValidationRules.requiredProcessingState(),
                        ValidationRules.minimumQuantity(ProductQuantity.grams(100)),
                        ValidationRules.minimumCleanliness(ProcessingFactor.of(600)),
                        ValidationRules.minimumEquipmentCondition(ProcessingFactor.of(500)),
                        ValidationRules.zeroOutputNotPermitted(),
                        ValidationRules.warning(EngineId.of("validation/test_balance_warning"), "Fixture values are not final balance")
                ),
                List.of(new ProcessingModifier(
                        EngineId.of("fixture/grind_quality_adjustment"),
                        "Fixture static grind quality adjustment",
                        ModifierCategory.QUALITY,
                        -10,
                        10
                )),
                false
        );
    }

    static ProcessingContext grindContext(Product product, List<ProcessingModifier> additionalModifiers) {
        return new ProcessingContext(
                product,
                grindBeefOperation(),
                ProcessingFactor.of(800),
                ProcessingFactor.of(600),
                ProcessingFactor.of(900),
                additionalModifiers
        );
    }

    static ProcessingModifier qualityModifier(String id, int effect, int priority) {
        return new ProcessingModifier(EngineId.of(id), "Quality fixture " + id, ModifierCategory.QUALITY, effect, priority);
    }

    static ProcessingModifier warningModifier(String id, int priority) {
        return new ProcessingModifier(EngineId.of(id), "Warning fixture " + id, ModifierCategory.WARNING, 0, priority);
    }

    static ProcessingModifier yieldModifier(String id, int basisPoints, int priority) {
        return new ProcessingModifier(EngineId.of(id), "Yield fixture " + id, ModifierCategory.YIELD, basisPoints, priority);
    }
}
