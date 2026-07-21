package com.butchercraft.client.screen;

import com.butchercraft.machine.packaging.PackagingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client-only placeholder screen for the inventory foundation of the Packaging Table.
 */
public final class PackagingTableScreen extends AbstractProcessingWorkstationScreen<PackagingTableMenu> {
    public PackagingTableScreen(PackagingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, BACKGROUND_COLOR);
        guiGraphics.fill(left + 6, top + 16, left + imageWidth - 6, top + 76, PANEL_COLOR);
        for (int inputIndex = 0; inputIndex < menu.inputSlotCount(); inputIndex++) {
            renderSlotFrame(guiGraphics, left + menu.workstationSlotX(inputIndex) - 1, top + menu.workstationSlotY(inputIndex) - 1);
        }
        renderSlotFrame(
                guiGraphics,
                left + menu.workstationSlotX(menu.firstOutputSlot()) - 1,
                top + menu.workstationSlotY(menu.firstOutputSlot()) - 1
        );
        renderReservedProgressSpace(guiGraphics, left + 116, top + 39);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        drawCentered(guiGraphics, Component.translatable("container.butchercraft.packaging_table.slot.meat"), 40, 17);
        drawCentered(guiGraphics, Component.translatable("container.butchercraft.packaging_table.slot.tray"), 70, 17);
        drawCentered(guiGraphics, Component.translatable("container.butchercraft.packaging_table.slot.wrap"), 100, 17);
        drawCentered(guiGraphics, Component.translatable("container.butchercraft.packaging_table.slot.result"), 70, 75);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    private void renderReservedProgressSpace(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + 24, y + 6, PROGRESS_BACKGROUND_COLOR);
    }

    private void drawCentered(GuiGraphics guiGraphics, Component component, int centerX, int y) {
        String text = component.getString();
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, MUTED_TEXT_COLOR, false);
    }
}
