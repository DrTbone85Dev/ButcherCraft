package com.butchercraft.client.screen;

import com.butchercraft.machine.cuttingtable.CuttingTableMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CuttingTableScreen extends AbstractProcessingWorkstationScreen<CuttingTableMenu> {
    public CuttingTableScreen(CuttingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
