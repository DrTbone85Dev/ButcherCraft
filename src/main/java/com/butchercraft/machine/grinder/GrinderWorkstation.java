package com.butchercraft.machine.grinder;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.workstation.WorkstationCapability;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class GrinderWorkstation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "grinder");
    public static final ResourceLocation CAPABILITY_ID = BuiltInDefinitionIds.WORKSTATION_CAPABILITY_GRINDING;

    private GrinderWorkstation() {
    }

    public static WorkstationCapability capability() {
        return new WorkstationCapability(
                ID,
                Set.of(),
                Set.of(CAPABILITY_ID),
                Set.of(),
                10_000,
                true,
                true,
                1,
                1
        );
    }
}
