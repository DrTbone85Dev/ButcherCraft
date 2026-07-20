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

        assertEquals(3, registry.size());
        assertTrue(registry.contains(TransformationId.of("butchercraft:grind_beef")));
        assertTrue(registry.contains(TransformationId.of("butchercraft:grind_pork")));
        assertTrue(registry.contains(TransformationId.of("butchercraft:grind_bison")));
        assertEquals(List.of(
                        "butchercraft:grind_beef",
                        "butchercraft:grind_pork",
                        "butchercraft:grind_bison"
                ),
                registry.findByCapability(BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING)
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
