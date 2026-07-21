package com.butchercraft.product.datapack;

import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.product.definition.ProductRegistry;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDatapackResourceTest {
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

        assertEquals(14, registry.size());
        assertEquals(List.of(
                        "butchercraft:beef_trim",
                        "butchercraft:ground_beef",
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
                        "butchercraft:beef_bone"
                ),
                registry.stream()
                        .map(definition -> definition.id().value())
                        .toList());
    }

    @Test
    void productRegistryServiceStartsFromBundledDatapackResources() {
        var resources = ProductRegistryService.bundledResources();

        assertEquals(BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS, resources.keySet().stream().toList());
        assertEquals(BuiltInProductRegistry.builtInRegistry().stream().toList(),
                ProductRegistryService.currentRegistry().stream().toList());
    }
}
