package com.butchercraft.transformation.datapack;

import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.TransformationId;
import com.butchercraft.transformation.TransformationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationDatapackLoaderTest {
    @AfterEach
    void resetRegistryService() {
        TransformationRegistryService.resetToBundledRegistry();
    }

    @Test
    void bundledDatapackResourcesLoadInStableOrder() {
        TransformationDatapackLoadResult result = loader().load(TransformationRegistryService.bundledResources());

        assertTrue(result.succeeded());
        assertEquals(List.of(
                        "butchercraft:grind_beef",
                        "butchercraft:grind_pork",
                        "butchercraft:grind_bison",
                        "butchercraft:break_beef_forequarter"
                ),
                result.registry().orElseThrow().stream()
                        .map(definition -> definition.id().value())
                        .toList());
    }

    @Test
    void duplicateTransformationIdsAreRejectedBeforeRegistryBuild() {
        TransformationDatapackLoadResult result = loader().load(resources(
                Map.entry("first", validGrindBeef()),
                Map.entry("second", validGrindBeef())
        ));

        assertFalse(result.succeeded());
        assertEquals(List.of(TransformationDatapackErrorCode.DUPLICATE_ID, TransformationDatapackErrorCode.DUPLICATE_ID),
                result.errors().stream().map(TransformationDatapackValidationError::code).toList());
    }

    @Test
    void unknownProductReferencesAreRejected() {
        TransformationDatapackLoadResult result = loader().load(resources(Map.entry(
                "unknown_product",
                validGrindBeef().replace("butchercraft:beef_trim", "butchercraft:missing_trim")
        )));

        assertFalse(result.succeeded());
        assertEquals(TransformationDatapackErrorCode.UNKNOWN_PRODUCT, result.errors().getFirst().code());
    }

    @Test
    void unknownCapabilitiesAreRejected() {
        TransformationDatapackLoadResult result = loader().load(resources(Map.entry(
                "unknown_capability",
                validGrindBeef().replace("butchercraft:grinding", "butchercraft:smoking")
        )));

        assertFalse(result.succeeded());
        assertEquals(TransformationDatapackErrorCode.UNKNOWN_CAPABILITY, result.errors().getFirst().code());
    }

    @Test
    void unsupportedSchemaVersionsAreRejected() {
        TransformationDatapackLoadResult result = loader().load(resources(Map.entry(
                "unsupported_schema",
                validGrindBeef().replace("\"schema_version\": 1", "\"schema_version\": 99")
        )));

        assertFalse(result.succeeded());
        assertEquals(TransformationDatapackErrorCode.UNSUPPORTED_SCHEMA_VERSION, result.errors().getFirst().code());
    }

    @Test
    void malformedJsonStructuresAreRejected() {
        TransformationDatapackLoadResult result = loader().load(resources(Map.entry(
                "missing_inputs",
                validGrindBeef().replace("\"inputs\"", "\"inputz\"")
        )));

        assertFalse(result.succeeded());
        assertEquals(TransformationDatapackErrorCode.MALFORMED_JSON, result.errors().getFirst().code());
    }

    @Test
    void malformedTransformationDefinitionsAreRejected() {
        TransformationDatapackLoadResult result = loader().load(resources(Map.entry(
                "bad_yield",
                validGrindBeef().replace("\"quantity\": 90", "\"quantity\": 91")
        )));

        assertFalse(result.succeeded());
        assertEquals(TransformationDatapackErrorCode.MALFORMED_DEFINITION, result.errors().getFirst().code());
    }

    @Test
    void registryReplacementIsReloadSafe() {
        TransformationDatapackLoadResult replacement = TransformationRegistryService.replaceFromDatapack(resources(Map.entry(
                "single",
                validGrindBeef()
        )));

        assertTrue(replacement.succeeded());
        assertEquals(1, TransformationRegistryService.currentRegistry().size());
        assertTrue(TransformationRegistryService.currentRegistry().contains(TransformationId.of("butchercraft:grind_beef")));

        TransformationDatapackLoadResult failedReload = TransformationRegistryService.replaceFromDatapack(resources(Map.entry(
                "failed",
                validGrindBeef().replace("butchercraft:beef_trim", "butchercraft:missing_trim")
        )));

        assertFalse(failedReload.succeeded());
        assertEquals(1, TransformationRegistryService.currentRegistry().size());
        assertTrue(TransformationRegistryService.currentRegistry().contains(TransformationId.of("butchercraft:grind_beef")));
    }

    private static TransformationDatapackLoader loader() {
        return new TransformationDatapackLoader(
                BuiltInProductRegistry.builtInRegistry(),
                Set.of(
                        BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING,
                        BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_BANDSAW
                )
        );
    }

    @SafeVarargs
    private static Map<String, JsonElement> resources(Map.Entry<String, String>... entries) {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            resources.put(entry.getKey(), JsonParser.parseString(entry.getValue()));
        }
        return resources;
    }

    private static String validGrindBeef() {
        return """
                {
                  "schema_version": 1,
                  "id": "butchercraft:grind_beef",
                  "display_name": "Grind Beef",
                  "required_capability": "butchercraft:grinding",
                  "inputs": [
                    {
                      "product_id": "butchercraft:beef_trim",
                      "quantity": 100,
                      "unit": "gram"
                    }
                  ],
                  "outputs": [
                    {
                      "product_id": "butchercraft:ground_beef",
                      "quantity": 90,
                      "unit": "gram",
                      "classification": "primary"
                    }
                  ],
                  "duration": {
                    "milliseconds": 3000
                  },
                  "yield": {
                    "numerator": 9,
                    "denominator": 10
                  },
                  "metadata": {
                    "butchercraft:schema/source": "test"
                  }
                }
                """;
    }
}
