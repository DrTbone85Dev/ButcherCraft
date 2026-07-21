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
            "data/butchercraft/butchercraft/content/packaging/retail_package.json",
            "data/butchercraft/butchercraft/content/packaging/vacuum_package.json",
            "data/butchercraft/butchercraft/content/packaging/butcher_paper_package.json",
            "data/butchercraft/butchercraft/content/packaging/freezer_paper_package.json"
    );

    public static final EngineId RETAIL_PACKAGE = EngineId.of("butchercraft:retail_package");
    public static final EngineId VACUUM_PACKAGE = EngineId.of("butchercraft:vacuum_package");
    public static final EngineId BUTCHER_PAPER_PACKAGE = EngineId.of("butchercraft:butcher_paper_package");
    public static final EngineId FREEZER_PAPER_PACKAGE = EngineId.of("butchercraft:freezer_paper_package");
    public static final EngineId TAG_RETAIL_PACKAGED = EngineId.of("butchercraft:trait/retail_packaged");
    public static final EngineId METADATA_SOURCE = EngineId.of("butchercraft:schema/source");
    public static final EngineId FOAM_TRAY = EngineId.of("butchercraft:foam_tray");
    public static final EngineId PLASTIC_WRAP_ROLL = EngineId.of("butchercraft:plastic_wrap_roll");
    public static final EngineId VACUUM_BAG = EngineId.of("butchercraft:vacuum_bag");
    public static final EngineId BUTCHER_PAPER_ROLL = EngineId.of("butchercraft:butcher_paper_roll");
    public static final EngineId FREEZER_PAPER_ROLL = EngineId.of("butchercraft:freezer_paper_roll");
    public static final EngineId RETAIL_LABEL_ROLL = EngineId.of("butchercraft:retail_label_roll");
    public static final List<EngineId> BUILT_IN_SUPPLY_ITEM_IDS = List.of(
            FOAM_TRAY,
            PLASTIC_WRAP_ROLL,
            VACUUM_BAG,
            BUTCHER_PAPER_ROLL,
            FREEZER_PAPER_ROLL,
            RETAIL_LABEL_ROLL
    );

    private BuiltInPackagingRegistry() {
    }

    public static PackagingRegistry builtInRegistry() {
        return PackagingRegistryService.loadBundledRegistry();
    }
}
