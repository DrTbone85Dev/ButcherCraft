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
        imageWidth = PackagingTableGuiLayout.IMAGE_WIDTH;
        imageHeight = PackagingTableGuiLayout.IMAGE_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        guiGraphics.blit(
                PackagingTableGuiLayout.BACKGROUND_TEXTURE,
                left,
                top,
                0,
                0,
                imageWidth,
                imageHeight,
                PackagingTableGuiLayout.TEXTURE_WIDTH,
                PackagingTableGuiLayout.TEXTURE_HEIGHT
        );
        for (int inputIndex = 0; inputIndex < menu.inputSlotCount(); inputIndex++) {
            renderSlotFrame(guiGraphics, left + menu.workstationSlotX(inputIndex) - 1, top + menu.workstationSlotY(inputIndex) - 1);
        }
        renderSlotFrame(
                guiGraphics,
                left + menu.workstationSlotX(menu.firstOutputSlot()) - 1,
                top + menu.workstationSlotY(menu.firstOutputSlot()) - 1
        );
        renderProgress(
                guiGraphics,
                left + PackagingTableGuiLayout.PROGRESS_X,
                top + PackagingTableGuiLayout.PROGRESS_Y,
                PackagingTableGuiLayout.PROGRESS_WIDTH,
                PackagingTableGuiLayout.PROGRESS_HEIGHT
        );
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        drawCentered(
                guiGraphics,
                Component.translatable("container.butchercraft.packaging_table.slot.meat"),
                PackagingTableGuiLayout.MEAT_LABEL_CENTER_X,
                PackagingTableGuiLayout.INPUT_LABEL_Y
        );
        drawCentered(
                guiGraphics,
                Component.translatable("container.butchercraft.packaging_table.slot.tray"),
                PackagingTableGuiLayout.SUPPLY_TRAY_LABEL_CENTER_X,
                PackagingTableGuiLayout.INPUT_LABEL_Y
        );
        drawCentered(
                guiGraphics,
                Component.translatable("container.butchercraft.packaging_table.slot.wrap"),
                PackagingTableGuiLayout.SUPPLY_WRAP_LABEL_CENTER_X,
                PackagingTableGuiLayout.INPUT_LABEL_Y
        );
        drawCentered(
                guiGraphics,
                Component.translatable("container.butchercraft.packaging_table.slot.result"),
                PackagingTableGuiLayout.RESULT_LABEL_CENTER_X,
                PackagingTableGuiLayout.RESULT_LABEL_Y
        );
        drawClippedStatus(
                guiGraphics,
                menu.statusComponent(),
                PackagingTableGuiLayout.STATUS_X,
                PackagingTableGuiLayout.STATUS_Y,
                PackagingTableGuiLayout.STATUS_WIDTH
        );
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    private void drawCentered(GuiGraphics guiGraphics, Component component, int centerX, int y) {
        String text = component.getString();
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, MUTED_TEXT_COLOR, false);
    }
}
