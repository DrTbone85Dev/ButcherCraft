package com.butchercraft.packaging.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.packaging.datapack.PackagingRegistryService;

import java.util.List;

/**
 * Built-in packaging datapack resource locations and stable packaging ids.
 */
public final class BuiltInPackagingRegistry {
    public static final String DATAPACK_DIRECTORY = "butchercraft/content/packaging";
    public static final List<String> BUILT_IN_RESOURCE_PATHS = List.of(
            "data/butchercraft/butchercraft/content/packaging/retail_package.json"
    );

    public static final EngineId RETAIL_PACKAGE = EngineId.of("butchercraft:retail_package");
    public static final EngineId TAG_RETAIL_PACKAGED = EngineId.of("butchercraft:trait/retail_packaged");
    public static final EngineId METADATA_SOURCE = EngineId.of("butchercraft:schema/source");

    private BuiltInPackagingRegistry() {
    }

    public static PackagingRegistry builtInRegistry() {
        return PackagingRegistryService.loadBundledRegistry();
    }
}
