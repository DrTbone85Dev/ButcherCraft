package com.butchercraft.machine.packaging;

import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * Server-authoritative menu for the Packaging Table placeholder inventory.
 */
public final class PackagingTableMenu extends ProcessingWorkstationMenu {
    public PackagingTableMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(
                ModMenuTypes.PACKAGING_TABLE.get(),
                containerId,
                playerInventory,
                extraData,
                ModBlocks.PACKAGING_TABLE.get(),
                PackagingTableWorkstation.capability()
        );
    }

    public PackagingTableMenu(int containerId, Inventory playerInventory, PackagingTableBlockEntity blockEntity) {
        super(ModMenuTypes.PACKAGING_TABLE.get(), containerId, playerInventory, blockEntity, ModBlocks.PACKAGING_TABLE.get());
    }
}
