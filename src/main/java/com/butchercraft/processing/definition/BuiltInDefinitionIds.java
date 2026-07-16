package com.butchercraft.processing.definition;

import com.butchercraft.ButcherCraft;
import net.minecraft.resources.ResourceLocation;

public final class BuiltInDefinitionIds {
    public static final ResourceLocation BEEF = id("beef");
    public static final ResourceLocation RED_MEAT = id("red_meat");
    public static final ResourceLocation BEEF_TRIM = id("beef_trim");
    public static final ResourceLocation GROUND_BEEF = id("ground_beef");
    public static final ResourceLocation GRIND_BEEF = id("grind_beef");

    public static final ResourceLocation OPERATION_CATEGORY_GRINDING = id("operation_category/grinding");
    public static final ResourceLocation WORKSTATION_CAPABILITY_GRINDING = id("grinding");
    public static final ResourceLocation WORKSTATION_CAPABILITY_DEVELOPMENT_PROCESSING =
            id("workstation_capability/development_processing");
    public static final ResourceLocation WORKFLOW_STAGE_PRIMARY_PROCESSING = id("workflow_stage/primary_processing");
    public static final ResourceLocation WORKFLOW_STAGE_SIZE_REDUCTION = id("workflow_stage/size_reduction");
    public static final ResourceLocation PROFILE_CATEGORY_RED_MEAT = id("profile_category/red_meat");

    private BuiltInDefinitionIds() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, path);
    }
}
