package com.butchercraft.packaging.datapack;

import com.butchercraft.packaging.definition.BuiltInPackagingRegistry;
import com.butchercraft.packaging.definition.PackagingRegistry;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingDatapackResourceTest {
    @Test
    void bundledPackagingResourcesExistAndLoad() throws IOException {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (String resourcePath : BuiltInPackagingRegistry.BUILT_IN_RESOURCE_PATHS) {
            var path = TestProjectPaths.projectPath("src/main/resources/" + resourcePath);
            assertTrue(Files.isRegularFile(path), "Missing packaging datapack resource " + resourcePath);
            try (var reader = Files.newBufferedReader(path)) {
                resources.put(resourcePath, JsonParser.parseReader(reader));
            }
        }

        PackagingDatapackLoadResult result = PackagingRegistryService.load(resources);

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(BuiltInPackagingRegistry.builtInRegistry().stream().toList(),
                result.registry().orElseThrow().stream().toList());
    }

    @Test
    void builtInRegistryIsLoadedFromPackagingDatapackResources() {
        PackagingRegistry registry = BuiltInPackagingRegistry.builtInRegistry();

        assertEquals(1, registry.size());
        assertEquals(List.of("butchercraft:retail_package"), registry.stream()
                .map(definition -> definition.id().value())
                .toList());
    }

    @Test
    void packagingRegistryServiceStartsFromBundledDatapackResources() {
        var resources = PackagingRegistryService.bundledResources();

        assertEquals(BuiltInPackagingRegistry.BUILT_IN_RESOURCE_PATHS, resources.keySet().stream().toList());
        assertEquals(BuiltInPackagingRegistry.builtInRegistry().stream().toList(),
                PackagingRegistryService.currentRegistry().stream().toList());
    }

    @Test
    void bundledPackagingResourcesUseContentSnapshotPath() {
        assertEquals("butchercraft/content/packaging", BuiltInPackagingRegistry.DATAPACK_DIRECTORY);
        for (String resourcePath : BuiltInPackagingRegistry.BUILT_IN_RESOURCE_PATHS) {
            assertTrue(
                    resourcePath.contains("/butchercraft/content/packaging/"),
                    "Packaging resources must live under the content snapshot path: " + resourcePath
            );
            assertFalse(
                    resourcePath.contains("/butchercraft/packaging/"),
                    "Packaging resources must not occupy a Minecraft registry path: " + resourcePath
            );
        }
    }
}
