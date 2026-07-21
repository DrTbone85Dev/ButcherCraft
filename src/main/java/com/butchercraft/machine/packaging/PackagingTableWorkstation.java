package com.butchercraft.machine.packaging;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.workstation.WorkstationCapability;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Stable Packaging Table identity and inventory layout for the workstation framework.
 *
 * <p>The table advertises a future packaging workstation capability, but v0.8.0 deliberately
 * does not execute packaging operations or transformations.</p>
 */
public final class PackagingTableWorkstation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "packaging_table");
    public static final ResourceLocation CAPABILITY_ID = BuiltInDefinitionIds.WORKSTATION_CAPABILITY_PACKAGING;

    private PackagingTableWorkstation() {
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
                3,
                1
        );
    }
}
