package com.butchercraft.client.screen;

import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ProcessingWorkstationScreen extends AbstractProcessingWorkstationScreen<ProcessingWorkstationMenu> {
    public ProcessingWorkstationScreen(ProcessingWorkstationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
