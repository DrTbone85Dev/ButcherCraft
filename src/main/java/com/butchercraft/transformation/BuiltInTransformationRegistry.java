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
    public static final EngineId WORKSTATION_CAPABILITY_BANDSAW = EngineId.of("butchercraft:bandsaw");

    private static final ProductQuantity GRINDER_BASIS_INPUT = ProductQuantity.grams(100);
    private static final ProductQuantity BANDSAW_BASIS_INPUT = ProductQuantity.grams(100_000);

    private BuiltInTransformationRegistry() {
    }

    public static TransformationRegistry builtInRegistry() {
        return TransformationRegistry.builder()
                .register(grindBeef())
                .register(grindPork())
                .register(grindBison())
                .register(breakBeefForequarter())
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

    public static TransformationDefinition breakBeefForequarter() {
        return TransformationDefinition.builder()
                .id("butchercraft:break_beef_forequarter")
                .displayName("Break Beef Forequarter")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(WORKSTATION_CAPABILITY_BANDSAW)
                .input(EngineId.of("butchercraft:beef_forequarter"), BANDSAW_BASIS_INPUT)
                .output(EngineId.of("butchercraft:beef_chuck"), ProductQuantity.grams(30_000), TransformationOutputClassification.PRIMARY)
                .output(EngineId.of("butchercraft:beef_rib"), ProductQuantity.grams(10_000), TransformationOutputClassification.PRIMARY)
                .output(EngineId.of("butchercraft:beef_packer_brisket"), ProductQuantity.grams(10_000), TransformationOutputClassification.PRIMARY)
                .output(EngineId.of("butchercraft:beef_plate"), ProductQuantity.grams(10_000), TransformationOutputClassification.PRIMARY)
                .output(EngineId.of("butchercraft:beef_shank"), ProductQuantity.grams(5_000), TransformationOutputClassification.PRIMARY)
                .output(EngineId.of("butchercraft:beef_trim"), ProductQuantity.grams(15_000), TransformationOutputClassification.BYPRODUCT)
                .output(EngineId.of("butchercraft:beef_fat"), ProductQuantity.grams(5_000), TransformationOutputClassification.BYPRODUCT)
                .output(EngineId.of("butchercraft:beef_bone"), ProductQuantity.grams(10_000), TransformationOutputClassification.WASTE)
                .duration(ProcessingDuration.milliseconds(6_000))
                .yield(new YieldRatio(19, 20))
                .metadata("butchercraft:schema/source", "built_in")
                .build();
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
