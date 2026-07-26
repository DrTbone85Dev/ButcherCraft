package com.butchercraft.machine.pattyformer;

import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public final class PattyFormerMenu extends ProcessingWorkstationMenu {
    public PattyFormerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(
                ModMenuTypes.PATTY_FORMER.get(),
                containerId,
                playerInventory,
                extraData,
                ModBlocks.PATTY_FORMER.get(),
                PattyFormerWorkstation.capability()
        );
    }

    public PattyFormerMenu(int containerId, Inventory playerInventory, PattyFormerBlockEntity blockEntity) {
        super(ModMenuTypes.PATTY_FORMER.get(), containerId, playerInventory, blockEntity,
                ModBlocks.PATTY_FORMER.get());
    }
}
