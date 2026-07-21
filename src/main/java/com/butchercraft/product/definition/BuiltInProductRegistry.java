package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.datapack.ProductRegistryService;

import java.util.List;

/**
 * Built-in product datapack resource locations and stable product ids.
 */
public final class BuiltInProductRegistry {
    public static final String DATAPACK_DIRECTORY = "butchercraft/content/product";
    public static final List<String> BUILT_IN_RESOURCE_PATHS = List.of(
            "data/butchercraft/butchercraft/content/product/beef_trim.json",
            "data/butchercraft/butchercraft/content/product/ground_beef.json",
            "data/butchercraft/butchercraft/content/product/retail_ground_beef.json",
            "data/butchercraft/butchercraft/content/product/pork_trim.json",
            "data/butchercraft/butchercraft/content/product/ground_pork.json",
            "data/butchercraft/butchercraft/content/product/bison_trim.json",
            "data/butchercraft/butchercraft/content/product/ground_bison.json",
            "data/butchercraft/butchercraft/content/product/beef_forequarter.json",
            "data/butchercraft/butchercraft/content/product/beef_chuck.json",
            "data/butchercraft/butchercraft/content/product/beef_rib.json",
            "data/butchercraft/butchercraft/content/product/beef_packer_brisket.json",
            "data/butchercraft/butchercraft/content/product/beef_plate.json",
            "data/butchercraft/butchercraft/content/product/beef_shank.json",
            "data/butchercraft/butchercraft/content/product/beef_fat.json",
            "data/butchercraft/butchercraft/content/product/beef_bone.json",
            "data/butchercraft/butchercraft/content/product/beef_hindquarter.json",
            "data/butchercraft/butchercraft/content/product/beef_round.json",
            "data/butchercraft/butchercraft/content/product/beef_sirloin.json",
            "data/butchercraft/butchercraft/content/product/beef_short_loin.json",
            "data/butchercraft/butchercraft/content/product/beef_flank.json",
            "data/butchercraft/butchercraft/content/product/t_bone_steak.json",
            "data/butchercraft/butchercraft/content/product/porterhouse_steak.json",
            "data/butchercraft/butchercraft/content/product/beef_strip_loin.json",
            "data/butchercraft/butchercraft/content/product/beef_tenderloin.json",
            "data/butchercraft/butchercraft/content/product/top_round.json",
            "data/butchercraft/butchercraft/content/product/bottom_round.json",
            "data/butchercraft/butchercraft/content/product/eye_of_round.json",
            "data/butchercraft/butchercraft/content/product/sirloin_tip.json",
            "data/butchercraft/butchercraft/content/product/top_sirloin.json",
            "data/butchercraft/butchercraft/content/product/sirloin_steak.json",
            "data/butchercraft/butchercraft/content/product/tri_tip.json"
    );

    public static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    public static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");
    public static final EngineId RETAIL_GROUND_BEEF = EngineId.of("butchercraft:retail_ground_beef");
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
    public static final EngineId BEEF_HINDQUARTER = EngineId.of("butchercraft:beef_hindquarter");
    public static final EngineId BEEF_ROUND = EngineId.of("butchercraft:beef_round");
    public static final EngineId BEEF_SIRLOIN = EngineId.of("butchercraft:beef_sirloin");
    public static final EngineId BEEF_SHORT_LOIN = EngineId.of("butchercraft:beef_short_loin");
    public static final EngineId BEEF_FLANK = EngineId.of("butchercraft:beef_flank");
    public static final EngineId T_BONE_STEAK = EngineId.of("butchercraft:t_bone_steak");
    public static final EngineId PORTERHOUSE_STEAK = EngineId.of("butchercraft:porterhouse_steak");
    public static final EngineId BEEF_STRIP_LOIN = EngineId.of("butchercraft:beef_strip_loin");
    public static final EngineId BEEF_TENDERLOIN = EngineId.of("butchercraft:beef_tenderloin");
    public static final EngineId TOP_ROUND = EngineId.of("butchercraft:top_round");
    public static final EngineId BOTTOM_ROUND = EngineId.of("butchercraft:bottom_round");
    public static final EngineId EYE_OF_ROUND = EngineId.of("butchercraft:eye_of_round");
    public static final EngineId SIRLOIN_TIP = EngineId.of("butchercraft:sirloin_tip");
    public static final EngineId TOP_SIRLOIN = EngineId.of("butchercraft:top_sirloin");
    public static final EngineId SIRLOIN_STEAK = EngineId.of("butchercraft:sirloin_steak");
    public static final EngineId TRI_TIP = EngineId.of("butchercraft:tri_tip");

    public static final EngineId CATEGORY_BEEF = EngineId.of("butchercraft:beef");
    public static final EngineId CATEGORY_PORK = EngineId.of("butchercraft:pork");
    public static final EngineId CATEGORY_BISON = EngineId.of("butchercraft:bison");

    public static final EngineId TAG_TRIM = EngineId.of("butchercraft:trait/trim");
    public static final EngineId TAG_GROUND = EngineId.of("butchercraft:trait/ground");
    public static final EngineId TAG_FOREQUARTER = EngineId.of("butchercraft:trait/forequarter");
    public static final EngineId TAG_PRIMAL = EngineId.of("butchercraft:trait/primal");
    public static final EngineId TAG_HINDQUARTER = EngineId.of("butchercraft:trait/hindquarter");
    public static final EngineId TAG_SUBPRIMAL = EngineId.of("butchercraft:trait/subprimal");
    public static final EngineId TAG_STEAK = EngineId.of("butchercraft:trait/steak");
    public static final EngineId TAG_FAT = EngineId.of("butchercraft:trait/fat");
    public static final EngineId TAG_BONE = EngineId.of("butchercraft:trait/bone");
    public static final EngineId TAG_RETAIL_PACKAGED = EngineId.of("butchercraft:trait/retail_packaged");
    public static final EngineId METADATA_SOURCE = EngineId.of("butchercraft:schema/source");

    private BuiltInProductRegistry() {
    }

    public static ProductRegistry builtInRegistry() {
        return ProductRegistryService.loadBundledRegistry();
    }
}
