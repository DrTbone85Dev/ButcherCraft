package com.butchercraft.processing.definition;

import com.butchercraft.ButcherCraft;
import net.minecraft.resources.ResourceLocation;

public final class BuiltInDefinitionIds {
    public static final ResourceLocation BEEF = id("beef");
    public static final ResourceLocation PORK = id("pork");
    public static final ResourceLocation BISON = id("bison");
    public static final ResourceLocation RED_MEAT = id("red_meat");
    public static final ResourceLocation BEEF_TRIM = id("beef_trim");
    public static final ResourceLocation GROUND_BEEF = id("ground_beef");
    public static final ResourceLocation RETAIL_GROUND_BEEF = id("retail_ground_beef");
    public static final ResourceLocation RETAIL_PACKAGE = id("retail_package");
    public static final ResourceLocation PORK_TRIM = id("pork_trim");
    public static final ResourceLocation GROUND_PORK = id("ground_pork");
    public static final ResourceLocation BISON_TRIM = id("bison_trim");
    public static final ResourceLocation GROUND_BISON = id("ground_bison");
    public static final ResourceLocation BEEF_FOREQUARTER = id("beef_forequarter");
    public static final ResourceLocation BEEF_CHUCK = id("beef_chuck");
    public static final ResourceLocation BEEF_RIB = id("beef_rib");
    public static final ResourceLocation BEEF_PACKER_BRISKET = id("beef_packer_brisket");
    public static final ResourceLocation BEEF_PLATE = id("beef_plate");
    public static final ResourceLocation BEEF_SHANK = id("beef_shank");
    public static final ResourceLocation BEEF_FAT = id("beef_fat");
    public static final ResourceLocation BEEF_BONE = id("beef_bone");
    public static final ResourceLocation BEEF_HINDQUARTER = id("beef_hindquarter");
    public static final ResourceLocation BEEF_ROUND = id("beef_round");
    public static final ResourceLocation BEEF_SIRLOIN = id("beef_sirloin");
    public static final ResourceLocation BEEF_SHORT_LOIN = id("beef_short_loin");
    public static final ResourceLocation BEEF_FLANK = id("beef_flank");
    public static final ResourceLocation T_BONE_STEAK = id("t_bone_steak");
    public static final ResourceLocation PORTERHOUSE_STEAK = id("porterhouse_steak");
    public static final ResourceLocation BEEF_STRIP_LOIN = id("beef_strip_loin");
    public static final ResourceLocation BEEF_TENDERLOIN = id("beef_tenderloin");
    public static final ResourceLocation TOP_ROUND = id("top_round");
    public static final ResourceLocation BOTTOM_ROUND = id("bottom_round");
    public static final ResourceLocation EYE_OF_ROUND = id("eye_of_round");
    public static final ResourceLocation SIRLOIN_TIP = id("sirloin_tip");
    public static final ResourceLocation TOP_SIRLOIN = id("top_sirloin");
    public static final ResourceLocation SIRLOIN_STEAK = id("sirloin_steak");
    public static final ResourceLocation TRI_TIP = id("tri_tip");
    public static final ResourceLocation GRIND_BEEF = id("grind_beef");
    public static final ResourceLocation GRIND_PORK = id("grind_pork");
    public static final ResourceLocation GRIND_BISON = id("grind_bison");
    public static final ResourceLocation BREAK_BEEF_FOREQUARTER = id("break_beef_forequarter");
    public static final ResourceLocation BREAK_BEEF_HINDQUARTER = id("break_beef_hindquarter");
    public static final ResourceLocation CUT_BEEF_SHORT_LOIN = id("cut_beef_short_loin");
    public static final ResourceLocation CUT_BEEF_ROUND = id("cut_beef_round");
    public static final ResourceLocation CUT_BEEF_SIRLOIN = id("cut_beef_sirloin");
    public static final ResourceLocation PACKAGE_RETAIL = id("package_retail");

    public static final ResourceLocation OPERATION_CATEGORY_GRINDING = id("operation_category/grinding");
    public static final ResourceLocation OPERATION_CATEGORY_FABRICATION = id("operation_category/fabrication");
    public static final ResourceLocation OPERATION_CATEGORY_PACKAGING = id("operation_category/packaging");
    public static final ResourceLocation WORKSTATION_CAPABILITY_GRINDING = id("grinding");
    public static final ResourceLocation WORKSTATION_CAPABILITY_BANDSAW = id("bandsaw");
    public static final ResourceLocation WORKSTATION_CAPABILITY_PACKAGING = id("packaging");
    public static final ResourceLocation WORKSTATION_CAPABILITY_DEVELOPMENT_PROCESSING =
            id("workstation_capability/development_processing");
    public static final ResourceLocation WORKFLOW_STAGE_PRIMARY_PROCESSING = id("workflow_stage/primary_processing");
    public static final ResourceLocation WORKFLOW_STAGE_SIZE_REDUCTION = id("workflow_stage/size_reduction");
    public static final ResourceLocation WORKFLOW_STAGE_FABRICATION = id("workflow_stage/fabrication");
    public static final ResourceLocation WORKFLOW_STAGE_RETAIL_PACKAGING = id("workflow_stage/retail_packaging");
    public static final ResourceLocation PROFILE_CATEGORY_RED_MEAT = id("profile_category/red_meat");

    private BuiltInDefinitionIds() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, path);
    }
}
