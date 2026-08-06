package com.butchercraft.machine.cuttingtable;

import com.butchercraft.ButcherCraft;
import com.butchercraft.workstation.WorkstationCapability;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class CuttingTableWorkstation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "cutting_table");

    private CuttingTableWorkstation() {
    }

    public static WorkstationCapability capability() {
        return new WorkstationCapability(
                ID,
                Set.of(),
                Set.of(),
                Set.of(),
                1_000,
                false,
                false,
                1,
                0
        );
    }
}
