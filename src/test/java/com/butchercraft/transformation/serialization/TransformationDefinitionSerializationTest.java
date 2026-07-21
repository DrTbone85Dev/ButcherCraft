package com.butchercraft.transformation.serialization;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.TransformationDefinition;
import com.butchercraft.transformation.TransformationOutputClassification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationDefinitionSerializationTest {
    private static final CanonicalTransformationDefinitionSerializer SERIALIZER =
            new CanonicalTransformationDefinitionSerializer();
    private static final CanonicalTransformationDefinitionDeserializer DESERIALIZER =
            new CanonicalTransformationDefinitionDeserializer();

    @Test
    void stableExternalFieldNamesAreFrozen() {
        assertEquals("schema_version", TransformationSerializedFieldNames.SCHEMA_VERSION);
        assertEquals("id", TransformationSerializedFieldNames.ID);
        assertEquals("display_name", TransformationSerializedFieldNames.DISPLAY_NAME);
        assertEquals("required_capability", TransformationSerializedFieldNames.REQUIRED_CAPABILITY);
        assertEquals("inputs", TransformationSerializedFieldNames.INPUTS);
        assertEquals("outputs", TransformationSerializedFieldNames.OUTPUTS);
        assertEquals("duration", TransformationSerializedFieldNames.DURATION);
        assertEquals("yield", TransformationSerializedFieldNames.YIELD);
        assertEquals("metadata", TransformationSerializedFieldNames.METADATA);
        assertEquals("product_id", TransformationSerializedFieldNames.PRODUCT_ID);
        assertEquals("quantity", TransformationSerializedFieldNames.QUANTITY);
        assertEquals("unit", TransformationSerializedFieldNames.UNIT);
        assertEquals("classification", TransformationSerializedFieldNames.CLASSIFICATION);
        assertEquals("milliseconds", TransformationSerializedFieldNames.MILLISECONDS);
        assertEquals("numerator", TransformationSerializedFieldNames.NUMERATOR);
        assertEquals("denominator", TransformationSerializedFieldNames.DENOMINATOR);
    }

    @Test
    void serializerCapturesEveryTransformationDefinitionField() {
        TransformationDefinition definition = completeDefinition();

        SerializedTransformationDefinition serialized = SERIALIZER.serialize(definition);

        assertEquals(TransformationSchemaVersion.CURRENT, serialized.schemaVersion());
        assertEquals("butchercraft:grind_beef", serialized.id());
        assertEquals("Grind Beef", serialized.displayName());
        assertEquals(Optional.of("butchercraft:grinding"), serialized.requiredCapability());
        assertEquals(1, serialized.inputs().size());
        assertEquals("butchercraft:beef_trim", serialized.inputs().getFirst().amount().productId());
        assertEquals(100, serialized.inputs().getFirst().amount().quantity());
        assertEquals("gram", serialized.inputs().getFirst().amount().unit());
        assertEquals(1, serialized.outputs().size());
        assertEquals("butchercraft:ground_beef", serialized.outputs().getFirst().amount().productId());
        assertEquals(90, serialized.outputs().getFirst().amount().quantity());
        assertEquals("gram", serialized.outputs().getFirst().amount().unit());
        assertEquals("primary", serialized.outputs().getFirst().classification());
        assertEquals(3_000, serialized.duration().milliseconds());
        assertEquals(9, serialized.yield().numerator());
        assertEquals(10, serialized.yield().denominator());
        assertEquals(Map.of("butchercraft:schema/source", "test"), serialized.metadata());
    }

    @Test
    void canonicalSerializationRoundTripsCompleteDefinitions() {
        TransformationDefinition definition = completeDefinition();

        TransformationDefinition deserialized = DESERIALIZER.deserialize(SERIALIZER.serialize(definition));

        assertEquals(definition, deserialized);
    }

    @Test
    void canonicalSerializationRoundTripsNoRequiredCapability() {
        TransformationDefinition definition = TransformationDefinition.builder()
                .id("butchercraft:capability_free")
                .displayName("Capability Free")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .noRequiredCapability()
                .input(EngineId.of("butchercraft:beef_trim"), ProductQuantity.grams(100))
                .output(EngineId.of("butchercraft:ground_beef"), ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10))
                .metadata("butchercraft:schema/source", "test")
                .build();

        SerializedTransformationDefinition serialized = SERIALIZER.serialize(definition);
        TransformationDefinition deserialized = DESERIALIZER.deserialize(serialized);

        assertTrue(serialized.requiredCapability().isEmpty());
        assertTrue(deserialized.requiredCapability().isEmpty());
        assertEquals(definition, deserialized);
    }

    @Test
    void builtInTransformationsRoundTripThroughCanonicalSerialization() {
        List<TransformationDefinition> definitions = BuiltInTransformationRegistry.builtInRegistry().stream().toList();

        assertEquals(4, definitions.size());
        assertEquals(definitions, definitions.stream()
                .map(SERIALIZER::serialize)
                .map(DESERIALIZER::deserialize)
                .toList());
    }

    @Test
    void serializedDefinitionsAreImmutableAndDefensivelyCopied() {
        List<SerializedTransformationInput> inputs = new ArrayList<>(List.of(new SerializedTransformationInput(
                new SerializedTransformationAmount("butchercraft:beef_trim", 100, "gram")
        )));
        List<SerializedTransformationOutput> outputs = new ArrayList<>(List.of(new SerializedTransformationOutput(
                new SerializedTransformationAmount("butchercraft:ground_beef", 90, "gram"),
                "primary"
        )));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("butchercraft:schema/source", "test");

        SerializedTransformationDefinition serialized = new SerializedTransformationDefinition(
                TransformationSchemaVersion.CURRENT,
                "butchercraft:grind_beef",
                "Grind Beef",
                Optional.of("butchercraft:grinding"),
                inputs,
                outputs,
                new SerializedTransformationDuration(3_000),
                new SerializedTransformationYield(9, 10),
                metadata
        );
        inputs.clear();
        outputs.clear();
        metadata.put("butchercraft:schema/changed", "changed");

        assertEquals(1, serialized.inputs().size());
        assertEquals(1, serialized.outputs().size());
        assertEquals(Map.of("butchercraft:schema/source", "test"), serialized.metadata());
        assertThrows(UnsupportedOperationException.class, () -> serialized.inputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> serialized.outputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> serialized.metadata().put("butchercraft:schema/source", "changed"));
    }

    @Test
    void deserializerRejectsUnsupportedSchemaVersionUntilMigrationsExist() {
        SerializedTransformationDefinition serialized = serialized(completeDefinition(), new TransformationSchemaVersion(99));

        assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(serialized));
    }

    @Test
    void deserializerRejectsInvalidSerializedDefinitionsThroughDomainValidation() {
        assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(serializedWithClassification("retail")));
        assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(serializedWithUnit("crate")));
        assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(serializedWithDuration(new SerializedTransformationDuration(0))));
        assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(serializedWithYield(new SerializedTransformationYield(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(serializedWithInputs(List.of())));
        assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(serializedWithDuplicateOutputs()));
    }

    @Test
    void serializedRecordsRejectInvalidStructure() {
        assertThrows(IllegalArgumentException.class, () -> new TransformationSchemaVersion(0));
        assertThrows(IllegalArgumentException.class, () -> new SerializedTransformationAmount(" ", 100, "gram"));
        assertThrows(IllegalArgumentException.class, () -> new SerializedTransformationAmount("butchercraft:beef_trim", 100, " "));
        assertThrows(IllegalArgumentException.class, () -> new SerializedTransformationOutput(
                new SerializedTransformationAmount("butchercraft:ground_beef", 90, "gram"),
                " "
        ));
        assertThrows(IllegalArgumentException.class, () -> new SerializedTransformationDefinition(
                TransformationSchemaVersion.CURRENT,
                " ",
                "Grind Beef",
                Optional.of("butchercraft:grinding"),
                List.of(new SerializedTransformationInput(new SerializedTransformationAmount("butchercraft:beef_trim", 100, "gram"))),
                List.of(new SerializedTransformationOutput(
                        new SerializedTransformationAmount("butchercraft:ground_beef", 90, "gram"),
                        "primary"
                )),
                new SerializedTransformationDuration(3_000),
                new SerializedTransformationYield(9, 10),
                Map.of("butchercraft:schema/source", "test")
        ));
        assertThrows(IllegalArgumentException.class, () -> new SerializedTransformationDefinition(
                TransformationSchemaVersion.CURRENT,
                "butchercraft:grind_beef",
                "Grind Beef",
                Optional.of(" "),
                List.of(new SerializedTransformationInput(new SerializedTransformationAmount("butchercraft:beef_trim", 100, "gram"))),
                List.of(new SerializedTransformationOutput(
                        new SerializedTransformationAmount("butchercraft:ground_beef", 90, "gram"),
                        "primary"
                )),
                new SerializedTransformationDuration(3_000),
                new SerializedTransformationYield(9, 10),
                Map.of("butchercraft:schema/source", "test")
        ));
        assertThrows(IllegalArgumentException.class, () -> new SerializedTransformationDefinition(
                TransformationSchemaVersion.CURRENT,
                "butchercraft:grind_beef",
                "Grind Beef",
                Optional.of("butchercraft:grinding"),
                List.of(new SerializedTransformationInput(new SerializedTransformationAmount("butchercraft:beef_trim", 100, "gram"))),
                List.of(new SerializedTransformationOutput(
                        new SerializedTransformationAmount("butchercraft:ground_beef", 90, "gram"),
                        "primary"
                )),
                new SerializedTransformationDuration(3_000),
                new SerializedTransformationYield(9, 10),
                Map.of("butchercraft:schema/source", " ")
        ));
    }

    @Test
    void serializerAndDeserializerRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> SERIALIZER.serialize(null));
        assertThrows(NullPointerException.class, () -> DESERIALIZER.deserialize(null));
    }

    private static TransformationDefinition completeDefinition() {
        return TransformationDefinition.builder()
                .id("butchercraft:grind_beef")
                .displayName("Grind Beef")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability("butchercraft:grinding")
                .input(EngineId.of("butchercraft:beef_trim"), ProductQuantity.grams(100))
                .output(EngineId.of("butchercraft:ground_beef"), ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10))
                .metadata("butchercraft:schema/source", "test")
                .build();
    }

    private static SerializedTransformationDefinition serialized(
            TransformationDefinition definition,
            TransformationSchemaVersion schemaVersion
    ) {
        SerializedTransformationDefinition serialized = SERIALIZER.serialize(definition);
        return new SerializedTransformationDefinition(
                schemaVersion,
                serialized.id(),
                serialized.displayName(),
                serialized.requiredCapability(),
                serialized.inputs(),
                serialized.outputs(),
                serialized.duration(),
                serialized.yield(),
                serialized.metadata()
        );
    }

    private static SerializedTransformationDefinition serializedWithClassification(String classification) {
        SerializedTransformationDefinition serialized = SERIALIZER.serialize(completeDefinition());
        return new SerializedTransformationDefinition(
                serialized.schemaVersion(),
                serialized.id(),
                serialized.displayName(),
                serialized.requiredCapability(),
                serialized.inputs(),
                List.of(new SerializedTransformationOutput(serialized.outputs().getFirst().amount(), classification)),
                serialized.duration(),
                serialized.yield(),
                serialized.metadata()
        );
    }

    private static SerializedTransformationDefinition serializedWithUnit(String unit) {
        SerializedTransformationDefinition serialized = SERIALIZER.serialize(completeDefinition());
        return new SerializedTransformationDefinition(
                serialized.schemaVersion(),
                serialized.id(),
                serialized.displayName(),
                serialized.requiredCapability(),
                List.of(new SerializedTransformationInput(new SerializedTransformationAmount(
                        serialized.inputs().getFirst().amount().productId(),
                        serialized.inputs().getFirst().amount().quantity(),
                        unit
                ))),
                serialized.outputs(),
                serialized.duration(),
                serialized.yield(),
                serialized.metadata()
        );
    }

    private static SerializedTransformationDefinition serializedWithYield(SerializedTransformationYield yield) {
        SerializedTransformationDefinition serialized = SERIALIZER.serialize(completeDefinition());
        return new SerializedTransformationDefinition(
                serialized.schemaVersion(),
                serialized.id(),
                serialized.displayName(),
                serialized.requiredCapability(),
                serialized.inputs(),
                serialized.outputs(),
                serialized.duration(),
                yield,
                serialized.metadata()
        );
    }

    private static SerializedTransformationDefinition serializedWithDuration(SerializedTransformationDuration duration) {
        SerializedTransformationDefinition serialized = SERIALIZER.serialize(completeDefinition());
        return new SerializedTransformationDefinition(
                serialized.schemaVersion(),
                serialized.id(),
                serialized.displayName(),
                serialized.requiredCapability(),
                serialized.inputs(),
                serialized.outputs(),
                duration,
                serialized.yield(),
                serialized.metadata()
        );
    }

    private static SerializedTransformationDefinition serializedWithInputs(List<SerializedTransformationInput> inputs) {
        SerializedTransformationDefinition serialized = SERIALIZER.serialize(completeDefinition());
        return new SerializedTransformationDefinition(
                serialized.schemaVersion(),
                serialized.id(),
                serialized.displayName(),
                serialized.requiredCapability(),
                inputs,
                serialized.outputs(),
                serialized.duration(),
                serialized.yield(),
                serialized.metadata()
        );
    }

    private static SerializedTransformationDefinition serializedWithDuplicateOutputs() {
        SerializedTransformationDefinition serialized = SERIALIZER.serialize(completeDefinition());
        return new SerializedTransformationDefinition(
                serialized.schemaVersion(),
                serialized.id(),
                serialized.displayName(),
                serialized.requiredCapability(),
                serialized.inputs(),
                List.of(serialized.outputs().getFirst(), serialized.outputs().getFirst()),
                serialized.duration(),
                new SerializedTransformationYield(18, 10),
                serialized.metadata()
        );
    }
}
