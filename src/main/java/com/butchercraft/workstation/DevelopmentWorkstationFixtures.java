package com.butchercraft.workstation;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class DevelopmentWorkstationFixtures {
    public static final ResourceLocation DEVELOPMENT_WORKSTATION_CAPABILITY =
            BuiltInDefinitionIds.WORKSTATION_CAPABILITY_DEVELOPMENT_PROCESSING;

    private DevelopmentWorkstationFixtures() {
    }

    public static WorkstationCapability capability() {
        return new WorkstationCapability(
                ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "development_processing_workstation"),
                Set.of(BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING),
                Set.of(DEVELOPMENT_WORKSTATION_CAPABILITY, BuiltInDefinitionIds.WORKSTATION_CAPABILITY_GRINDING),
                Set.of(BuiltInDefinitionIds.RED_MEAT),
                10_000,
                true,
                true,
                1,
                1
        );
    }
}
