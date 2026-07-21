package com.butchercraft.product.datapack;

import com.butchercraft.content.ContentSnapshot;
import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.product.definition.ProductRegistry;
import com.google.gson.JsonElement;

import java.util.Map;
import java.util.Objects;

/**
 * Compatibility facade for accessing the active product registry.
 */
public final class ProductRegistryService {
    private ProductRegistryService() {
    }

    public static ProductRegistry currentRegistry() {
        return ContentSnapshotService.currentProductRegistry();
    }

    public static void replaceRegistry(ProductRegistry registry) {
        ContentSnapshotService.replaceSnapshot(new ContentSnapshot(
                Objects.requireNonNull(registry, "registry"),
                ContentSnapshotService.currentPackagingRegistry(),
                ContentSnapshotService.currentTransformationRegistry()
        ));
    }

    public static ProductDatapackLoadResult load(Map<String, JsonElement> resources) {
        return ContentSnapshotService.newProductLoader().load(resources);
    }

    public static void resetToBundledRegistry() {
        ContentSnapshotService.resetToBundledSnapshot();
    }

    public static ProductRegistry loadBundledRegistry() {
        return ContentSnapshotService.loadBundledProductRegistry();
    }

    public static Map<String, JsonElement> bundledResources() {
        return ContentSnapshotService.bundledProductResources();
    }
}
