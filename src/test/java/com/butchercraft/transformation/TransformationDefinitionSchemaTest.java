package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.quantity.ProductQuantity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationDefinitionSchemaTest {
    private static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    private static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");
    private static final EngineId GRINDING = EngineId.of("butchercraft:grinding");
    private static final EngineId SOURCE_METADATA = EngineId.of("butchercraft:schema/source");

    @Test
    void builderCreatesCompleteCanonicalDefinition() {
        TransformationDefinition definition = validBuilder().build();

        assertEquals(TransformationId.of("butchercraft:grind_beef"), definition.id());
        assertEquals("Grind Beef", definition.displayName());
        assertEquals(TransformationDefinition.CURRENT_SCHEMA_VERSION, definition.schemaVersion());
        assertEquals(Optional.of(GRINDING), definition.requiredCapability());
        assertEquals(Optional.of(GRINDING), definition.workstationCapability());
        assertEquals(List.of(BEEF_TRIM), definition.inputs().stream()
                .map(input -> input.requiredAmount().materialId())
                .toList());
        assertEquals(List.of(GROUND_BEEF), definition.outputs().stream()
                .map(output -> output.producedAmount().materialId())
                .toList());
        assertEquals(ProcessingDuration.milliseconds(3_000), definition.duration());
        assertEquals(new YieldRatio(9, 10), definition.yield());
        assertEquals("built_in", definition.metadata().get(SOURCE_METADATA));
    }

    @Test
    void builderRejectsIncompleteDefinitions() {
        assertThrows(IllegalStateException.class, () -> validBuilderWithoutId().build());
        assertThrows(IllegalStateException.class, () -> validBuilderWithoutDisplayName().build());
        assertThrows(IllegalStateException.class, () -> validBuilderWithoutSchemaVersion().build());
        assertThrows(IllegalStateException.class, () -> validBuilderWithoutRequiredCapability().build());
        assertThrows(IllegalStateException.class, () -> validBuilderWithoutDuration().build());
        assertThrows(IllegalStateException.class, () -> validBuilderWithoutYield().build());
        assertThrows(IllegalArgumentException.class, () -> validBuilderWithoutInputs().build());
        assertThrows(IllegalArgumentException.class, () -> validBuilderWithoutOutputs().build());
    }

    @Test
    void definitionConstructionValidatesCanonicalFields() {
        assertThrows(IllegalArgumentException.class, () -> validBuilder().displayName("  ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().schemaVersion(0).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder()
                .output(EngineId.of("butchercraft:extra_ground_beef"), ProductQuantity.grams(1), TransformationOutputClassification.BYPRODUCT)
                .build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder()
                .metadata(SOURCE_METADATA, " ")
                .build());
    }

    @Test
    void definitionRejectsYieldThatDoesNotMatchDeclaredQuantities() {
        assertThrows(IllegalArgumentException.class, () -> validBuilder()
                .yield(YieldRatio.identity())
                .build());
    }

    @Test
    void definitionDefensivelyCopiesCollectionsAndMetadata() {
        List<TransformationInput> inputs = new ArrayList<>(List.of(input(BEEF_TRIM, 100)));
        List<TransformationOutput> outputs = new ArrayList<>(List.of(output(GROUND_BEEF, 90)));
        Map<EngineId, String> metadata = new LinkedHashMap<>();
        metadata.put(SOURCE_METADATA, " built_in ");

        TransformationDefinition definition = new TransformationDefinition(
                TransformationId.of("butchercraft:grind_beef"),
                " Grind Beef ",
                TransformationDefinition.CURRENT_SCHEMA_VERSION,
                Optional.of(GRINDING),
                inputs,
                outputs,
                ProcessingDuration.milliseconds(3_000),
                new YieldRatio(9, 10),
                metadata
        );
        inputs.clear();
        outputs.clear();
        metadata.put(EngineId.of("butchercraft:schema/changed"), "changed");

        assertEquals("Grind Beef", definition.displayName());
        assertEquals(List.of(BEEF_TRIM), definition.inputs().stream()
                .map(transformationInput -> transformationInput.requiredAmount().materialId())
                .toList());
        assertEquals(List.of(GROUND_BEEF), definition.outputs().stream()
                .map(transformationOutput -> transformationOutput.producedAmount().materialId())
                .toList());
        assertEquals(Map.of(SOURCE_METADATA, "built_in"), definition.metadata());
        assertThrows(UnsupportedOperationException.class, () -> definition.inputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.outputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.metadata().put(SOURCE_METADATA, "changed"));
    }

    @Test
    void equalityIncludesCanonicalSchemaFields() {
        TransformationDefinition base = validBuilder().build();

        assertEquals(base, validBuilder().build());
        assertNotEquals(base, validBuilder().displayName("Different Name").build());
        assertNotEquals(base, validBuilder().metadata("butchercraft:schema/extra", "value").build());
        assertNotEquals(base, TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(200))
                .output(GROUND_BEEF, ProductQuantity.grams(180), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10))
                .metadata(SOURCE_METADATA, "built_in")
                .build());
    }

    @Test
    void legacyConstructorPopulatesCanonicalDefaultsForCompatibility() {
        TransformationDefinition definition = new TransformationDefinition(
                TransformationId.of("butchercraft:grind_beef"),
                List.of(input(BEEF_TRIM, 100)),
                List.of(output(GROUND_BEEF, 90)),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(GRINDING)
        );

        assertEquals("butchercraft:grind_beef", definition.displayName());
        assertEquals(TransformationDefinition.CURRENT_SCHEMA_VERSION, definition.schemaVersion());
        assertEquals(Optional.of(GRINDING), definition.requiredCapability());
        assertEquals(new YieldRatio(9, 10), definition.yield());
        assertTrue(definition.metadata().isEmpty());
    }

    @Test
    void builderAllowsExplicitNoRequiredCapability() {
        TransformationDefinition definition = validBuilder()
                .noRequiredCapability()
                .build();

        assertTrue(definition.requiredCapability().isEmpty());
    }

    private static TransformationDefinition.Builder validBuilder() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10))
                .metadata(SOURCE_METADATA, "built_in");
    }

    private static TransformationDefinition.Builder validBuilderWithoutId() {
        return TransformationDefinition.builder()
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10));
    }

    private static TransformationDefinition.Builder validBuilderWithoutDisplayName() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10));
    }

    private static TransformationDefinition.Builder validBuilderWithoutSchemaVersion() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10));
    }

    private static TransformationDefinition.Builder validBuilderWithoutRequiredCapability() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10));
    }

    private static TransformationDefinition.Builder validBuilderWithoutInputs() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10));
    }

    private static TransformationDefinition.Builder validBuilderWithoutOutputs() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10));
    }

    private static TransformationDefinition.Builder validBuilderWithoutDuration() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .yield(new YieldRatio(9, 10));
    }

    private static TransformationDefinition.Builder validBuilderWithoutYield() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(BEEF_TRIM, ProductQuantity.grams(100))
                .output(GROUND_BEEF, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000));
    }

    private static TransformationInput input(EngineId materialId, long grams) {
        return new TransformationInput(new MaterialAmount(materialId, ProductQuantity.grams(grams)));
    }

    private static TransformationOutput output(EngineId materialId, long grams) {
        return new TransformationOutput(
                new MaterialAmount(materialId, ProductQuantity.grams(grams)),
                TransformationOutputClassification.PRIMARY
        );
    }
}
