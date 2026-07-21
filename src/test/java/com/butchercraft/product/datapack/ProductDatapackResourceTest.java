package com.butchercraft.product.datapack;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.product.definition.ProductRegistry;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDatapackResourceTest {
    private static final List<String> EXPECTED_BUNDLED_PRODUCT_IDS = List.of(
            "butchercraft:beef_trim",
            "butchercraft:ground_beef",
            "butchercraft:retail_ground_beef",
            "butchercraft:pork_trim",
            "butchercraft:ground_pork",
            "butchercraft:bison_trim",
            "butchercraft:ground_bison",
            "butchercraft:beef_forequarter",
            "butchercraft:beef_chuck",
            "butchercraft:beef_rib",
            "butchercraft:beef_packer_brisket",
            "butchercraft:beef_plate",
            "butchercraft:beef_shank",
            "butchercraft:beef_fat",
            "butchercraft:beef_bone",
            "butchercraft:beef_hindquarter",
            "butchercraft:beef_round",
            "butchercraft:beef_sirloin",
            "butchercraft:beef_short_loin",
            "butchercraft:beef_flank",
            "butchercraft:t_bone_steak",
            "butchercraft:porterhouse_steak",
            "butchercraft:beef_strip_loin",
            "butchercraft:beef_tenderloin",
            "butchercraft:top_round",
            "butchercraft:bottom_round",
            "butchercraft:eye_of_round",
            "butchercraft:sirloin_tip",
            "butchercraft:top_sirloin",
            "butchercraft:sirloin_steak",
            "butchercraft:tri_tip"
    );
    private static final List<String> V070_BEEF_FABRICATION_PRODUCT_IDS = List.of(
            "butchercraft:beef_hindquarter",
            "butchercraft:beef_round",
            "butchercraft:beef_sirloin",
            "butchercraft:beef_short_loin",
            "butchercraft:beef_flank",
            "butchercraft:t_bone_steak",
            "butchercraft:porterhouse_steak",
            "butchercraft:beef_strip_loin",
            "butchercraft:beef_tenderloin",
            "butchercraft:top_round",
            "butchercraft:bottom_round",
            "butchercraft:eye_of_round",
            "butchercraft:sirloin_tip",
            "butchercraft:top_sirloin",
            "butchercraft:sirloin_steak",
            "butchercraft:tri_tip"
    );

    @Test
    void bundledProductResourcesExistAndLoad() throws IOException {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (String resourcePath : BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS) {
            var path = TestProjectPaths.projectPath("src/main/resources/" + resourcePath);
            assertTrue(Files.isRegularFile(path), "Missing product datapack resource " + resourcePath);
            try (var reader = Files.newBufferedReader(path)) {
                resources.put(resourcePath, JsonParser.parseReader(reader));
            }
        }

        ProductDatapackLoadResult result = ProductRegistryService.load(resources);

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(BuiltInProductRegistry.builtInRegistry().stream().toList(),
                result.registry().orElseThrow().stream().toList());
    }

    @Test
    void builtInRegistryIsLoadedFromProductDatapackResources() {
        ProductRegistry registry = BuiltInProductRegistry.builtInRegistry();

        assertEquals(31, registry.size());
        assertEquals(EXPECTED_BUNDLED_PRODUCT_IDS, registry.stream()
                        .map(definition -> definition.id().value())
                        .toList());
        var retail = registry.find(BuiltInProductRegistry.RETAIL_GROUND_BEEF).orElseThrow();
        assertEquals(BuiltInProductRegistry.TAG_RETAIL_PACKAGED, retail.tags().stream().findFirst().orElseThrow());
        assertEquals("butchercraft:retail_package",
                retail.packagingMetadata().orElseThrow().packagingDefinitionId().value());
        assertEquals("butchercraft:ground_beef",
                retail.packagingMetadata().orElseThrow().sourceProductId().value());
    }

    @Test
    void allV070BeefFabricationProductsLoadFromBundledResources() {
        ProductRegistry registry = BuiltInProductRegistry.builtInRegistry();

        for (String productId : V070_BEEF_FABRICATION_PRODUCT_IDS) {
            assertTrue(registry.contains(EngineId.of(productId)), "Missing " + productId);
        }
    }

    @Test
    void productRegistryServiceStartsFromBundledDatapackResources() {
        var resources = ProductRegistryService.bundledResources();

        assertEquals(BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS, resources.keySet().stream().toList());
        assertEquals(BuiltInProductRegistry.builtInRegistry().stream().toList(),
                ProductRegistryService.currentRegistry().stream().toList());
    }

    @Test
    void bundledSerializedProductResourcesUseContentSnapshotPath() {
        assertEquals("butchercraft/content/product", BuiltInProductRegistry.DATAPACK_DIRECTORY);
        for (String resourcePath : BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS) {
            assertTrue(
                    resourcePath.contains("/butchercraft/content/product/"),
                    "Serialized product resource must live under the content snapshot path: " + resourcePath
            );
            assertFalse(
                    resourcePath.contains("/butchercraft/product/"),
                    "Serialized product resource must not occupy the Minecraft product registry path: " + resourcePath
            );
        }
    }

    @Test
    void serializedProductResourcesDoNotOccupyMinecraftProductRegistryDirectory() throws IOException {
        Path registryDirectory = TestProjectPaths.projectPath("src/main/resources/data/butchercraft/butchercraft/product");
        if (!Files.exists(registryDirectory)) {
            return;
        }

        try (var entries = Files.list(registryDirectory)) {
            assertTrue(entries.findAny().isEmpty(), "Serialized products must not be placed in " + registryDirectory);
        }
    }
}
