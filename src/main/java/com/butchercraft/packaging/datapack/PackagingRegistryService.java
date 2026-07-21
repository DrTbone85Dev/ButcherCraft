package com.butchercraft.packaging.datapack;

import com.butchercraft.content.ContentSnapshot;
import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.packaging.definition.PackagingRegistry;
import com.google.gson.JsonElement;

import java.util.Map;
import java.util.Objects;

/**
 * Compatibility facade for accessing the active packaging registry.
 */
public final class PackagingRegistryService {
    private PackagingRegistryService() {
    }

    public static PackagingRegistry currentRegistry() {
        return ContentSnapshotService.currentPackagingRegistry();
    }

    public static void replaceRegistry(PackagingRegistry registry) {
        ContentSnapshotService.replaceSnapshot(new ContentSnapshot(
                ContentSnapshotService.currentProductRegistry(),
                Objects.requireNonNull(registry, "registry"),
                ContentSnapshotService.currentTransformationRegistry()
        ));
    }

    public static PackagingDatapackLoadResult load(Map<String, JsonElement> resources) {
        return ContentSnapshotService.newPackagingLoader().load(resources);
    }

    public static void resetToBundledRegistry() {
        ContentSnapshotService.resetToBundledSnapshot();
    }

    public static PackagingRegistry loadBundledRegistry() {
        return ContentSnapshotService.loadBundledPackagingRegistry();
    }

    public static Map<String, JsonElement> bundledResources() {
        return ContentSnapshotService.bundledPackagingResources();
    }
}
