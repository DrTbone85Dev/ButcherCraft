package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.List;
import java.util.Optional;

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
                "butchercraft:beef_trim",
                "butchercraft:ground_beef"
        );
    }

    public static TransformationDefinition grindPork() {
        return grind(
                "butchercraft:grind_pork",
                "butchercraft:pork_trim",
                "butchercraft:ground_pork"
        );
    }

    public static TransformationDefinition grindBison() {
        return grind(
                "butchercraft:grind_bison",
                "butchercraft:bison_trim",
                "butchercraft:ground_bison"
        );
    }

    private static TransformationDefinition grind(String id, String inputProduct, String outputProduct) {
        return new TransformationDefinition(
                TransformationId.of(id),
                List.of(new TransformationInput(new MaterialAmount(EngineId.of(inputProduct), GRINDER_BASIS_INPUT))),
                List.of(new TransformationOutput(
                        new MaterialAmount(EngineId.of(outputProduct), ProductQuantity.grams(90)),
                        TransformationOutputClassification.PRIMARY
                )),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(WORKSTATION_CAPABILITY_GRINDING)
        );
    }
}
