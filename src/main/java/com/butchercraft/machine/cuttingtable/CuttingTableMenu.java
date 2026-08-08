package com.butchercraft.machine.cuttingtable;

import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public final class CuttingTableMenu extends ProcessingWorkstationMenu {
    public CuttingTableMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(
                ModMenuTypes.CUTTING_TABLE.get(),
                containerId,
                playerInventory,
                extraData,
                ModBlocks.CUTTING_TABLE.get(),
                CuttingTableWorkstation.capability()
        );
    }

    public CuttingTableMenu(int containerId, Inventory playerInventory, CuttingTableBlockEntity blockEntity) {
        super(ModMenuTypes.CUTTING_TABLE.get(), containerId, playerInventory, blockEntity, ModBlocks.CUTTING_TABLE.get());
    }

    @Override
    public int workstationSlotX(int slot) {
        return slot < firstOutputSlot() ? 26 : 108;
    }

    @Override
    public int workstationSlotY(int slot) {
        if (slot < firstOutputSlot()) {
            return 35;
        }
        return slot == firstOutputSlot() ? 27 : 62;
    }
}
