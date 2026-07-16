package com.butchercraft.client.screen;

import com.butchercraft.machine.grinder.GrinderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class GrinderScreen extends AbstractProcessingWorkstationScreen<GrinderMenu> {
    public GrinderScreen(GrinderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
