package com.butchercraft.client.screen;

import com.butchercraft.machine.bandsaw.BandsawMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BandsawScreen extends AbstractProcessingWorkstationScreen<BandsawMenu> {
    public BandsawScreen(BandsawMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
