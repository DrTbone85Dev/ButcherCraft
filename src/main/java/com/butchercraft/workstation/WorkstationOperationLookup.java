package com.butchercraft.workstation;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface WorkstationOperationLookup {
    WorkstationOperationResolution resolve(
            RegistryAccess registryAccess,
            WorkstationCapability capability,
            ItemStack inputStack
    );
}
