package com.butchercraft.machine.bandsaw;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.workstation.WorkstationCapability;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class BandsawWorkstation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "bandsaw");
    public static final ResourceLocation CAPABILITY_ID = BuiltInDefinitionIds.WORKSTATION_CAPABILITY_BANDSAW;

    private BandsawWorkstation() {
    }

    public static WorkstationCapability capability() {
        return new WorkstationCapability(
                ID,
                Set.of(),
                Set.of(CAPABILITY_ID),
                Set.of(),
                250_000,
                true,
                true,
                1,
                8
        );
    }
}
