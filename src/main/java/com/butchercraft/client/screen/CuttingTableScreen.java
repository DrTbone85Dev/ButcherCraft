package com.butchercraft.client.screen;

import com.butchercraft.machine.cuttingtable.CuttingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CuttingTableScreen extends AbstractProcessingWorkstationScreen<CuttingTableMenu> {
    public CuttingTableScreen(CuttingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        guiGraphics.drawString(
                font,
                Component.translatable("screen.butchercraft.cutting_table.input"),
                8,
                24,
                MUTED_TEXT_COLOR,
                false
        );
        guiGraphics.drawString(
                font,
                Component.translatable("screen.butchercraft.cutting_table.primary_output"),
                70,
                16,
                MUTED_TEXT_COLOR,
                false
        );
        guiGraphics.drawString(
                font,
                Component.translatable("screen.butchercraft.cutting_table.trim_output"),
                70,
                51,
                MUTED_TEXT_COLOR,
                false
        );
    }
}
