package com.butchercraft.integration.datapack;

import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.engine.EngineId;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.TransformationId;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentDatapackReloadListenerTest {
    @AfterEach
    void resetSnapshot() {
        ContentSnapshotService.resetToBundledSnapshot();
    }

    @Test
    void reloadListenerIgnoresMinecraftProductRegistryResourcesWhenLoadingContentSnapshot() {
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        resources.put(id("product/runtime_registry_product"), JsonParser.parseString(runtimeProductDefinitionJson()));
        resources.put(id("content/product/test_input"), JsonParser.parseString(serializedProductJson(
                "butchercraft:test_input",
                "Test Input"
        )));
        resources.put(id("content/product/test_output"), JsonParser.parseString(serializedProductJson(
                "butchercraft:test_output",
                "Test Output"
        )));
        resources.put(id("transformation/test_transform"), JsonParser.parseString(serializedTransformationJson(
                "butchercraft:test_transform",
                "butchercraft:test_input",
                "butchercraft:test_output"
        )));

        new ContentDatapackReloadListener().apply(resources, null, null);

        assertEquals(2, ContentSnapshotService.currentProductRegistry().size());
        assertEquals(1, ContentSnapshotService.currentTransformationRegistry().size());
        assertTrue(ContentSnapshotService.currentProductRegistry().contains(EngineId.of("butchercraft:test_input")));
        assertTrue(ContentSnapshotService.currentProductRegistry().contains(EngineId.of("butchercraft:test_output")));
        assertTrue(ContentSnapshotService.currentTransformationRegistry()
                .contains(TransformationId.of("butchercraft:test_transform")));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("butchercraft", path);
    }

    private static String runtimeProductDefinitionJson() {
        return """
                {
                  "display_name_key": "definition.butchercraft.product.runtime_registry_product",
                  "species": "butchercraft:beef",
                  "product_category": "butchercraft:beef",
                  "processing_state": "butchercraft:trim",
                  "quantity_unit": "gram",
                  "edible": true,
                  "bone_state": "boneless",
                  "spoilage_eligible": true,
                  "traits": [],
                  "graph_input": true,
                  "graph_output": false
                }
                """;
    }

    private static String serializedProductJson(String id, String displayName) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "display_name": "%s",
                  "category": "butchercraft:beef",
                  "default_quantity_unit": "gram",
                  "tags": [
                    "butchercraft:trait/test"
                  ],
                  "metadata": {
                    "butchercraft:schema/source": "test"
                  }
                }
                """.formatted(id, displayName);
    }

    private static String serializedTransformationJson(String id, String inputProduct, String outputProduct) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "display_name": "Test Transform",
                  "required_capability": "%s",
                  "inputs": [
                    {
                      "product_id": "%s",
                      "quantity": 100,
                      "unit": "gram"
                    }
                  ],
                  "outputs": [
                    {
                      "product_id": "%s",
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
                """.formatted(id, BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING.value(),
                inputProduct, outputProduct);
    }
}
