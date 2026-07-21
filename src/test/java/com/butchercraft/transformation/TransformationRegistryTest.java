package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.quantity.ProductQuantity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationRegistryTest {
    private static final EngineId GRINDING = EngineId.of("butchercraft:grinding");
    private static final EngineId CUTTING = EngineId.of("butchercraft:cutting");
    private static final List<String> EXPECTED_BUILT_IN_TRANSFORMATION_IDS = List.of(
            "butchercraft:grind_beef",
            "butchercraft:grind_pork",
            "butchercraft:grind_bison",
            "butchercraft:break_beef_forequarter",
            "butchercraft:break_beef_hindquarter",
            "butchercraft:cut_beef_short_loin",
            "butchercraft:cut_beef_round",
            "butchercraft:cut_beef_sirloin"
    );

    @Test
    void builderRegistersDefinitionsIntoImmutableRegistryInInsertionOrder() {
        TransformationRegistryBuilder builder = TransformationRegistry.builder()
                .register(definition("butchercraft:first", "butchercraft:first_input", "butchercraft:first_output", GRINDING))
                .register(definition("butchercraft:second", "butchercraft:second_input", "butchercraft:second_output", CUTTING));

        TransformationRegistry registry = builder.build();
        builder.register(definition("butchercraft:third", "butchercraft:third_input", "butchercraft:third_output", GRINDING));

        assertEquals(2, registry.size());
        assertTrue(registry.contains(TransformationId.of("butchercraft:first")));
        assertFalse(registry.contains(TransformationId.of("butchercraft:third")));
        assertEquals("butchercraft:first", registry.find(TransformationId.of("butchercraft:first")).orElseThrow().id().value());
        assertTrue(registry.find(TransformationId.of("butchercraft:missing")).isEmpty());
        assertEquals(List.of("butchercraft:first", "butchercraft:second"), registry.stream()
                .map(definition -> definition.id().value())
                .toList());
    }

    @Test
    void builderRejectsDuplicateIdsAndNullDefinitions() {
        TransformationDefinition definition =
                definition("butchercraft:duplicate", "butchercraft:input", "butchercraft:output", GRINDING);
        TransformationRegistryBuilder builder = TransformationRegistry.builder().register(definition);

        assertThrows(IllegalArgumentException.class, () -> builder.register(definition));
        assertThrows(NullPointerException.class, () -> TransformationRegistry.builder().register(null));
    }

    @Test
    void lookupMethodsRejectNullIdsAndCapabilities() {
        TransformationRegistry registry = TransformationRegistry.builder().build();

        assertThrows(NullPointerException.class, () -> registry.contains(null));
        assertThrows(NullPointerException.class, () -> registry.find(null));
        assertThrows(NullPointerException.class, () -> registry.findByCapability(null));
    }

    @Test
    void findByCapabilityPreservesRegistrationOrder() {
        TransformationRegistry registry = TransformationRegistry.builder()
                .register(definition("butchercraft:first", "butchercraft:first_input", "butchercraft:first_output", GRINDING))
                .register(definition("butchercraft:second", "butchercraft:second_input", "butchercraft:second_output", CUTTING))
                .register(definition("butchercraft:third", "butchercraft:third_input", "butchercraft:third_output", GRINDING))
                .build();

        assertEquals(List.of("butchercraft:first", "butchercraft:third"), registry.findByCapability(GRINDING)
                .map(definition -> definition.id().value())
                .toList());
        assertTrue(registry.findByCapability(EngineId.of("butchercraft:smoking")).findAny().isEmpty());
    }

    @Test
    void builtInRegistryContainsExistingGrinderTransformations() {
        TransformationRegistry registry = BuiltInTransformationRegistry.builtInRegistry();

        assertEquals(8, registry.size());
        assertEquals(EXPECTED_BUILT_IN_TRANSFORMATION_IDS, registry.stream()
                .map(definition -> definition.id().value())
                .toList());
        assertEquals(List.of(
                        "butchercraft:grind_beef",
                        "butchercraft:grind_pork",
                        "butchercraft:grind_bison"
                ),
                registry.findByCapability(BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING)
                        .map(definition -> definition.id().value())
                        .toList());
        assertEquals(List.of(
                        "butchercraft:break_beef_forequarter",
                        "butchercraft:break_beef_hindquarter",
                        "butchercraft:cut_beef_short_loin",
                        "butchercraft:cut_beef_round",
                        "butchercraft:cut_beef_sirloin"
                ),
                registry.findByCapability(BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_BANDSAW)
                        .map(definition -> definition.id().value())
                        .toList());
    }

    @Test
    void registeredGrinderDefinitionsRebaseToConcreteInputQuantity() {
        TransformationDefinition definition = BuiltInTransformationRegistry.builtInRegistry()
                .find(TransformationId.of("butchercraft:grind_beef"))
                .orElseThrow()
                .withInputQuantity(ProductQuantity.grams(1_000));

        assertEquals(1_000, definition.inputs().getFirst().requiredAmount().quantity().amount());
        assertEquals(900, definition.outputs().getFirst().producedAmount().quantity().amount());
        assertEquals(Optional.of(BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING), definition.workstationCapability());
    }

    @Test
    void registeredBandsawDefinitionDefinesOrderedForequarterOutputs() {
        TransformationDefinition definition = BuiltInTransformationRegistry.builtInRegistry()
                .find(TransformationId.of("butchercraft:break_beef_forequarter"))
                .orElseThrow();

        assertEquals(ProductQuantity.grams(100_000), definition.inputs().getFirst().requiredAmount().quantity());
        assertEquals(Optional.of(BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_BANDSAW), definition.workstationCapability());
        assertEquals(List.of(
                        "butchercraft:beef_chuck",
                        "butchercraft:beef_rib",
                        "butchercraft:beef_packer_brisket",
                        "butchercraft:beef_plate",
                        "butchercraft:beef_shank",
                        "butchercraft:beef_trim",
                        "butchercraft:beef_fat",
                        "butchercraft:beef_bone"
                ),
                definition.outputs().stream()
                        .map(output -> output.producedAmount().materialId().value())
                        .toList());
        assertEquals(List.of(30_000L, 10_000L, 10_000L, 10_000L, 5_000L, 15_000L, 5_000L, 10_000L),
                definition.outputs().stream()
                        .map(output -> output.producedAmount().quantity().amount())
                        .toList());
    }

    @Test
    void registeredBandsawDefinitionsDefineOrderedHindquarterAndPrimalOutputs() {
        assertBandsawOutputs(
                "butchercraft:break_beef_hindquarter",
                "butchercraft:beef_hindquarter",
                100_000,
                List.of(
                        "butchercraft:beef_round",
                        "butchercraft:beef_sirloin",
                        "butchercraft:beef_short_loin",
                        "butchercraft:beef_flank",
                        "butchercraft:beef_trim",
                        "butchercraft:beef_fat",
                        "butchercraft:beef_bone"
                ),
                List.of(30_000L, 15_000L, 15_000L, 7_500L, 15_000L, 7_500L, 10_000L)
        );
        assertBandsawOutputs(
                "butchercraft:cut_beef_short_loin",
                "butchercraft:beef_short_loin",
                15_000,
                List.of(
                        "butchercraft:t_bone_steak",
                        "butchercraft:porterhouse_steak",
                        "butchercraft:beef_strip_loin",
                        "butchercraft:beef_tenderloin",
                        "butchercraft:beef_trim",
                        "butchercraft:beef_bone"
                ),
                List.of(4_000L, 3_000L, 3_000L, 2_000L, 1_500L, 1_500L)
        );
        assertBandsawOutputs(
                "butchercraft:cut_beef_round",
                "butchercraft:beef_round",
                30_000,
                List.of(
                        "butchercraft:top_round",
                        "butchercraft:bottom_round",
                        "butchercraft:eye_of_round",
                        "butchercraft:sirloin_tip",
                        "butchercraft:beef_trim",
                        "butchercraft:beef_fat",
                        "butchercraft:beef_bone"
                ),
                List.of(7_500L, 6_500L, 3_500L, 5_000L, 4_000L, 1_500L, 2_000L)
        );
        assertBandsawOutputs(
                "butchercraft:cut_beef_sirloin",
                "butchercraft:beef_sirloin",
                15_000,
                List.of(
                        "butchercraft:top_sirloin",
                        "butchercraft:sirloin_steak",
                        "butchercraft:tri_tip",
                        "butchercraft:beef_trim",
                        "butchercraft:beef_fat",
                        "butchercraft:beef_bone"
                ),
                List.of(5_000L, 3_500L, 2_000L, 2_500L, 1_000L, 1_000L)
        );
    }

    private static void assertBandsawOutputs(
            String transformationId,
            String inputProduct,
            long inputQuantity,
            List<String> outputProducts,
            List<Long> outputQuantities
    ) {
        TransformationDefinition definition = BuiltInTransformationRegistry.builtInRegistry()
                .find(TransformationId.of(transformationId))
                .orElseThrow();

        assertEquals(inputProduct, definition.inputs().getFirst().requiredAmount().materialId().value());
        assertEquals(inputQuantity, definition.inputs().getFirst().requiredAmount().quantity().amount());
        assertEquals(Optional.of(BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_BANDSAW), definition.workstationCapability());
        assertEquals(outputProducts, definition.outputs().stream()
                .map(output -> output.producedAmount().materialId().value())
                .toList());
        assertEquals(outputQuantities, definition.outputs().stream()
                .map(output -> output.producedAmount().quantity().amount())
                .toList());
    }

    private static TransformationDefinition definition(
            String id,
            String inputProduct,
            String outputProduct,
            EngineId capability
    ) {
        return new TransformationDefinition(
                TransformationId.of(id),
                List.of(new TransformationInput(new MaterialAmount(EngineId.of(inputProduct), ProductQuantity.grams(100)))),
                List.of(new TransformationOutput(
                        new MaterialAmount(EngineId.of(outputProduct), ProductQuantity.grams(90)),
                        TransformationOutputClassification.PRIMARY
                )),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(capability)
        );
    }
}
