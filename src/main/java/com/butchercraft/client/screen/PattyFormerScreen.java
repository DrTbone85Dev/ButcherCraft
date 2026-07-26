package com.butchercraft.client.screen;

import com.butchercraft.machine.pattyformer.PattyFormerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PattyFormerScreen extends AbstractProcessingWorkstationScreen<PattyFormerMenu> {
    public PattyFormerScreen(PattyFormerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
