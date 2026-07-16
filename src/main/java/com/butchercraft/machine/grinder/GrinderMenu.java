package com.butchercraft.machine.grinder;

import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public final class GrinderMenu extends ProcessingWorkstationMenu {
    public GrinderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.GRINDER.get(), containerId, playerInventory, extraData, ModBlocks.GRINDER.get(), GrinderWorkstation.capability());
    }

    public GrinderMenu(int containerId, Inventory playerInventory, GrinderBlockEntity blockEntity) {
        super(ModMenuTypes.GRINDER.get(), containerId, playerInventory, blockEntity, ModBlocks.GRINDER.get());
    }
}
