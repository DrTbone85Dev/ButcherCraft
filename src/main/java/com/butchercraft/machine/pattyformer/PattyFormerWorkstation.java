package com.butchercraft.machine.pattyformer;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.workstation.WorkstationCapability;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class PattyFormerWorkstation {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "patty_former");
    public static final ResourceLocation CAPABILITY_ID =
            BuiltInDefinitionIds.WORKSTATION_CAPABILITY_PATTY_FORMING;

    private PattyFormerWorkstation() {
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
