package com.butchercraft.transformation.datapack;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.TransformationId;
import com.butchercraft.transformation.TransformationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationDatapackResourceTest {
    private static final List<String> EXPECTED_BUNDLED_TRANSFORMATION_IDS = List.of(
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
    void bundledTransformationResourcesExistAndLoad() throws IOException {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (String resourcePath : BuiltInTransformationRegistry.BUILT_IN_RESOURCE_PATHS) {
            var path = TestProjectPaths.projectPath("src/main/resources/" + resourcePath);
            assertTrue(Files.isRegularFile(path), "Missing transformation datapack resource " + resourcePath);
            try (var reader = Files.newBufferedReader(path)) {
                resources.put(resourcePath, JsonParser.parseReader(reader));
            }
        }

        TransformationDatapackLoadResult result = TransformationRegistryService.load(resources);

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(BuiltInTransformationRegistry.builtInRegistry().stream().toList(),
                result.registry().orElseThrow().stream().toList());
    }

    @Test
    void builtInRegistryIsLoadedFromTransformationDatapackResources() {
        TransformationRegistry registry = BuiltInTransformationRegistry.builtInRegistry();

        assertEquals(8, registry.size());
        assertEquals(EXPECTED_BUNDLED_TRANSFORMATION_IDS, registry.stream()
                        .map(definition -> definition.id().value())
                        .toList());
        assertTrue(registry.contains(TransformationId.of("butchercraft:break_beef_forequarter")));
        assertTrue(registry.contains(TransformationId.of("butchercraft:break_beef_hindquarter")));
    }

    @Test
    void transformationRegistryServiceStartsFromBundledDatapackResources() {
        Map<String, JsonElement> resources = TransformationRegistryService.bundledResources();

        assertEquals(BuiltInTransformationRegistry.BUILT_IN_RESOURCE_PATHS, resources.keySet().stream().toList());
        assertEquals(BuiltInTransformationRegistry.builtInRegistry().stream().toList(),
                TransformationRegistryService.currentRegistry().stream().toList());
    }
}
