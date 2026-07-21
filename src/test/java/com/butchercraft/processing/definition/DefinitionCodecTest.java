package com.butchercraft.processing.definition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionCodecTest {
    @Test
    void speciesDefinitionRoundTrips() {
        assertRoundTrip(SpeciesDefinition.CODEC, BuiltInProcessingDefinitions.beefSpecies());
    }

    @Test
    void processingProfileDefinitionRoundTrips() {
        assertRoundTrip(ProcessingProfileDefinition.CODEC, BuiltInProcessingDefinitions.redMeatProfile());
    }

    @Test
    void productDefinitionRoundTrips() {
        assertRoundTrip(ProductDefinition.CODEC, BuiltInProcessingDefinitions.beefTrimProduct());
        assertRoundTrip(ProductDefinition.CODEC, BuiltInProcessingDefinitions.retailGroundBeefProduct());
        assertEquals(BuiltInDefinitionIds.RETAIL_PACKAGE,
                BuiltInProcessingDefinitions.retailGroundBeefProduct().packaging().orElseThrow().definition());
        assertEquals(BuiltInDefinitionIds.GROUND_BEEF,
                BuiltInProcessingDefinitions.retailGroundBeefProduct().packaging().orElseThrow().sourceProduct());
    }

    @Test
    void operationDefinitionRoundTrips() {
        assertRoundTrip(ProcessingOperationDefinition.CODEC, BuiltInProcessingDefinitions.grindBeefOperation());
        assertRoundTrip(ProcessingOperationDefinition.CODEC, BuiltInProcessingDefinitions.breakBeefForequarterOperation());
        assertRoundTrip(ProcessingOperationDefinition.CODEC, BuiltInProcessingDefinitions.packageRetailOperation());
        assertEquals(1, BuiltInProcessingDefinitions.grindBeefOperation().outputs().size());
        assertEquals(8, BuiltInProcessingDefinitions.breakBeefForequarterOperation().outputs().size());
        assertEquals(1, BuiltInProcessingDefinitions.packageRetailOperation().outputs().size());
    }

    @Test
    void missingRequiredFieldFailsDecode() {
        JsonObject json = JsonParser.parseString("""
                {
                  "processing_profile": "butchercraft:red_meat",
                  "product_family": "butchercraft:beef",
                  "products_edible": true,
                  "enabled": true
                }
                """).getAsJsonObject();

        assertTrue(SpeciesDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void invalidReferenceIdentifierFailsDecode() {
        JsonObject json = JsonParser.parseString("""
                {
                  "display_name_key": "definition.test",
                  "processing_profile": "Not A Valid Id",
                  "product_family": "butchercraft:beef",
                  "products_edible": true,
                  "enabled": true
                }
                """).getAsJsonObject();

        assertTrue(SpeciesDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void invalidBoundedValueFailsDecode() {
        JsonObject json = JsonParser.parseString("""
                {
                  "amount": 100,
                  "unit": "crate"
                }
                """).getAsJsonObject();

        assertTrue(QuantityDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void unknownTypedValueFailsDecode() {
        assertTrue(BoneState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"cartilage_only\"")).error().isPresent());
        assertTrue(ZeroOutputPolicy.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"sometimes\"")).error().isPresent());
    }

    private static <T> void assertRoundTrip(Codec<T> codec, T value) {
        var encoded = codec.encodeStart(JsonOps.INSTANCE, value);
        assertTrue(encoded.error().isEmpty(), encoded.toString());
        var decoded = codec.parse(JsonOps.INSTANCE, encoded.result().orElseThrow());
        assertTrue(decoded.error().isEmpty(), decoded.toString());
        assertEquals(value, decoded.result().orElseThrow());
    }
}
