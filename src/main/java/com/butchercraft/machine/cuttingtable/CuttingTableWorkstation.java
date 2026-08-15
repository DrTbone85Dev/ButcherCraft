package com.butchercraft.machine.cuttingtable;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationSlotCapacityPolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class CuttingTableWorkstation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "cutting_table");
    public static final ResourceLocation CAPABILITY_ID = BuiltInDefinitionIds.WORKSTATION_CAPABILITY_CUTTING_TABLE;
    private static final WorkstationSlotCapacityPolicy SLOT_CAPACITY_POLICY =
            WorkstationSlotCapacityPolicy.perSlot(1, 1, 64);

    private CuttingTableWorkstation() {
    }

    public static WorkstationCapability capability() {
        return new WorkstationCapability(
                ID,
                Set.of(),
                Set.of(CAPABILITY_ID),
                Set.of(BuiltInDefinitionIds.RED_MEAT),
                15_000,
                true,
                false,
                1,
                2
        );
    }

    public static WorkstationSlotCapacityPolicy slotCapacityPolicy() {
        return SLOT_CAPACITY_POLICY;
    }
}
