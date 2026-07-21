package com.butchercraft.transformation.datapack;

import com.butchercraft.content.ContentSnapshot;
import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.transformation.TransformationRegistry;
import com.google.gson.JsonElement;

import java.util.Map;
import java.util.Objects;

/**
 * Compatibility facade for accessing the active transformation registry.
 */
public final class TransformationRegistryService {
    private TransformationRegistryService() {
    }

    public static TransformationRegistry currentRegistry() {
        return ContentSnapshotService.currentTransformationRegistry();
    }

    public static void replaceRegistry(TransformationRegistry registry) {
        ContentSnapshotService.replaceSnapshot(new ContentSnapshot(
                ContentSnapshotService.currentProductRegistry(),
                ContentSnapshotService.currentPackagingRegistry(),
                Objects.requireNonNull(registry, "registry")
        ));
    }

    public static TransformationDatapackLoadResult load(Map<String, JsonElement> resources) {
        return new TransformationDatapackLoader(
                ContentSnapshotService.currentProductRegistry(),
                ContentSnapshotService.knownCapabilities()
        ).load(resources);
    }

    public static TransformationDatapackLoadResult replaceFromDatapack(Map<String, JsonElement> resources) {
        TransformationDatapackLoadResult result = load(resources);
        result.registry().ifPresent(TransformationRegistryService::replaceRegistry);
        return result;
    }

    public static void resetToBundledRegistry() {
        ContentSnapshotService.resetToBundledSnapshot();
    }

    public static TransformationRegistry loadBundledRegistry() {
        return ContentSnapshotService.loadBundledTransformationRegistry();
    }

    public static Map<String, JsonElement> bundledResources() {
        return ContentSnapshotService.bundledTransformationResources();
    }
}
