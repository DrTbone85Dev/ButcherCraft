package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.datapack.ProductRegistryService;

import java.util.List;

/**
 * Built-in product datapack resource locations and stable product ids.
 */
public final class BuiltInProductRegistry {
    public static final String DATAPACK_DIRECTORY = "butchercraft/product";
    public static final List<String> BUILT_IN_RESOURCE_PATHS = List.of(
            "data/butchercraft/butchercraft/product/beef_trim.json",
            "data/butchercraft/butchercraft/product/ground_beef.json",
            "data/butchercraft/butchercraft/product/pork_trim.json",
            "data/butchercraft/butchercraft/product/ground_pork.json",
            "data/butchercraft/butchercraft/product/bison_trim.json",
            "data/butchercraft/butchercraft/product/ground_bison.json",
            "data/butchercraft/butchercraft/product/beef_forequarter.json",
            "data/butchercraft/butchercraft/product/beef_chuck.json",
            "data/butchercraft/butchercraft/product/beef_rib.json",
            "data/butchercraft/butchercraft/product/beef_packer_brisket.json",
            "data/butchercraft/butchercraft/product/beef_plate.json",
            "data/butchercraft/butchercraft/product/beef_shank.json",
            "data/butchercraft/butchercraft/product/beef_fat.json",
            "data/butchercraft/butchercraft/product/beef_bone.json"
    );

    public static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    public static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");
    public static final EngineId PORK_TRIM = EngineId.of("butchercraft:pork_trim");
    public static final EngineId GROUND_PORK = EngineId.of("butchercraft:ground_pork");
    public static final EngineId BISON_TRIM = EngineId.of("butchercraft:bison_trim");
    public static final EngineId GROUND_BISON = EngineId.of("butchercraft:ground_bison");
    public static final EngineId BEEF_FOREQUARTER = EngineId.of("butchercraft:beef_forequarter");
    public static final EngineId BEEF_CHUCK = EngineId.of("butchercraft:beef_chuck");
    public static final EngineId BEEF_RIB = EngineId.of("butchercraft:beef_rib");
    public static final EngineId BEEF_PACKER_BRISKET = EngineId.of("butchercraft:beef_packer_brisket");
    public static final EngineId BEEF_PLATE = EngineId.of("butchercraft:beef_plate");
    public static final EngineId BEEF_SHANK = EngineId.of("butchercraft:beef_shank");
    public static final EngineId BEEF_FAT = EngineId.of("butchercraft:beef_fat");
    public static final EngineId BEEF_BONE = EngineId.of("butchercraft:beef_bone");

    public static final EngineId CATEGORY_BEEF = EngineId.of("butchercraft:beef");
    public static final EngineId CATEGORY_PORK = EngineId.of("butchercraft:pork");
    public static final EngineId CATEGORY_BISON = EngineId.of("butchercraft:bison");

    public static final EngineId TAG_TRIM = EngineId.of("butchercraft:trait/trim");
    public static final EngineId TAG_GROUND = EngineId.of("butchercraft:trait/ground");
    public static final EngineId TAG_FOREQUARTER = EngineId.of("butchercraft:trait/forequarter");
    public static final EngineId TAG_PRIMAL = EngineId.of("butchercraft:trait/primal");
    public static final EngineId TAG_FAT = EngineId.of("butchercraft:trait/fat");
    public static final EngineId TAG_BONE = EngineId.of("butchercraft:trait/bone");
    public static final EngineId METADATA_SOURCE = EngineId.of("butchercraft:schema/source");

    private BuiltInProductRegistry() {
    }

    public static ProductRegistry builtInRegistry() {
        return ProductRegistryService.loadBundledRegistry();
    }
}
