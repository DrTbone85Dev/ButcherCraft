package com.butchercraft.machine.bandsaw;

import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public final class BandsawMenu extends ProcessingWorkstationMenu {
    public BandsawMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.BANDSAW.get(), containerId, playerInventory, extraData, ModBlocks.BANDSAW.get(), BandsawWorkstation.capability());
    }

    public BandsawMenu(int containerId, Inventory playerInventory, BandsawBlockEntity blockEntity) {
        super(ModMenuTypes.BANDSAW.get(), containerId, playerInventory, blockEntity, ModBlocks.BANDSAW.get());
    }
}
