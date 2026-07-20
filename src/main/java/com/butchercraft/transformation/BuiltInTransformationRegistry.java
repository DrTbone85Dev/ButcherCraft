package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.quantity.ProductQuantity;

/**
 * Built-in transformation definitions used before datapack transformation loading exists.
 */
public final class BuiltInTransformationRegistry {
    public static final EngineId WORKSTATION_CAPABILITY_GRINDING = EngineId.of("butchercraft:grinding");

    private static final ProductQuantity GRINDER_BASIS_INPUT = ProductQuantity.grams(100);

    private BuiltInTransformationRegistry() {
    }

    public static TransformationRegistry builtInRegistry() {
        return TransformationRegistry.builder()
                .register(grindBeef())
                .register(grindPork())
                .register(grindBison())
                .build();
    }

    public static TransformationDefinition grindBeef() {
        return grind(
                "butchercraft:grind_beef",
                "Grind Beef",
                "butchercraft:beef_trim",
                "butchercraft:ground_beef"
        );
    }

    public static TransformationDefinition grindPork() {
        return grind(
                "butchercraft:grind_pork",
                "Grind Pork",
                "butchercraft:pork_trim",
                "butchercraft:ground_pork"
        );
    }

    public static TransformationDefinition grindBison() {
        return grind(
                "butchercraft:grind_bison",
                "Grind Bison",
                "butchercraft:bison_trim",
                "butchercraft:ground_bison"
        );
    }

    private static TransformationDefinition grind(String id, String displayName, String inputProduct, String outputProduct) {
        return TransformationDefinition.builder()
                .id(id)
                .displayName(displayName)
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(WORKSTATION_CAPABILITY_GRINDING)
                .input(EngineId.of(inputProduct), GRINDER_BASIS_INPUT)
                .output(EngineId.of(outputProduct), ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10))
                .metadata("butchercraft:schema/source", "built_in")
                .build();
    }
}
