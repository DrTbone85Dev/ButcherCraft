package com.butchercraft.engine;

import com.butchercraft.engine.context.ProcessingContext;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.ProcessingOutputDefinition;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
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
    static final EngineId BEEF_FOREQUARTER = EngineId.of("butchercraft:beef_forequarter");
    static final EngineId BEEF_CHUCK = EngineId.of("butchercraft:beef_chuck");
    static final EngineId BEEF_RIB = EngineId.of("butchercraft:beef_rib");
    static final EngineId BEEF_BRISKET = EngineId.of("butchercraft:beef_brisket");
    static final EngineId BEEF_PLATE = EngineId.of("butchercraft:beef_plate");
    static final EngineId BEEF_SHANK = EngineId.of("butchercraft:beef_shank");
    static final EngineId BEEF_FAT = EngineId.of("butchercraft:beef_fat");
    static final EngineId BEEF_BONE = EngineId.of("butchercraft:beef_bone");
    static final EngineId BREAK_BEEF_FOREQUARTER = EngineId.of("butchercraft:break_beef_forequarter");

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

    static Product beefForequarter(long grams) {
        return new Product(
                BEEF_FOREQUARTER,
                ProductCategory.BEEF,
                ProcessingState.fromId(EngineId.of("butchercraft:forequarter")),
                ProductQuantity.grams(grams),
                ProductQuality.ofScore(700)
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

    static ProcessingOperation breakBeefForequarterOperation(boolean allowSmallRoundingOutputs) {
        return new ProcessingOperation(
                BREAK_BEEF_FOREQUARTER,
                "Break Beef Forequarter",
                BEEF_FOREQUARTER,
                Optional.of(ProductCategory.BEEF),
                ProcessingState.fromId(EngineId.of("butchercraft:forequarter")),
                ProcessingDuration.milliseconds(6_000),
                List.of(
                        output(BEEF_CHUCK, "butchercraft:primal", 30, allowSmallRoundingOutputs),
                        output(BEEF_RIB, "butchercraft:primal", 10, allowSmallRoundingOutputs),
                        output(BEEF_BRISKET, "butchercraft:primal", 10, allowSmallRoundingOutputs),
                        output(BEEF_PLATE, "butchercraft:primal", 10, allowSmallRoundingOutputs),
                        output(BEEF_SHANK, "butchercraft:primal", 5, allowSmallRoundingOutputs),
                        output(BEEF_TRIM, "butchercraft:trim", 15, allowSmallRoundingOutputs),
                        output(BEEF_FAT, "butchercraft:fat", 5, allowSmallRoundingOutputs),
                        output(BEEF_BONE, "butchercraft:bone", 10, allowSmallRoundingOutputs)
                ),
                List.of(
                        ValidationRules.requiredProductType(),
                        ValidationRules.requiredSourceCategory(),
                        ValidationRules.requiredProcessingState(),
                        ValidationRules.zeroOutputNotPermitted()
                ),
                List.of(),
                false
        );
    }

    static ProcessingContext forequarterContext(Product product, boolean allowSmallRoundingOutputs) {
        return ProcessingContext.neutral(product, breakBeefForequarterOperation(allowSmallRoundingOutputs));
    }

    private static ProcessingOutputDefinition output(EngineId product, String state, long percent, boolean allowZero) {
        return new ProcessingOutputDefinition(
                product,
                ProcessingState.fromId(EngineId.of(state)),
                new YieldRatio(percent, 100),
                -5,
                QuantityUnit.GRAM,
                allowZero
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
