package com.butchercraft.client.screen;

import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

abstract class AbstractProcessingWorkstationScreen<T extends ProcessingWorkstationMenu> extends AbstractContainerScreen<T> {
    protected static final int BACKGROUND_COLOR = 0xFF2B3036;
    protected static final int PANEL_COLOR = 0xFF3A4149;
    protected static final int SLOT_COLOR = 0xFF171A1E;
    protected static final int SLOT_BORDER_COLOR = 0xFF68717A;
    protected static final int PROGRESS_BACKGROUND_COLOR = 0xFF1D2227;
    protected static final int PROGRESS_FILL_COLOR = 0xFF70B87A;
    protected static final int TEXT_COLOR = 0xFFE6E8EA;
    protected static final int MUTED_TEXT_COLOR = 0xFFB8C0C8;

    AbstractProcessingWorkstationScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
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
        for (int outputIndex = 0; outputIndex < menu.outputSlotCount(); outputIndex++) {
            int slot = menu.firstOutputSlot() + outputIndex;
            renderSlotFrame(guiGraphics, left + menu.workstationSlotX(slot) - 1, top + menu.workstationSlotY(slot) - 1);
        }
        renderProgress(guiGraphics, left + 52, top + 41);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        drawClippedStatus(guiGraphics, menu.statusComponent(), 8, 66, 160);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    protected void renderSlotFrame(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 18, y + 18, SLOT_BORDER_COLOR);
        guiGraphics.fill(x, y, x + 17, y + 17, SLOT_COLOR);
    }

    protected void renderProgress(GuiGraphics guiGraphics, int x, int y) {
        renderProgress(guiGraphics, x, y, 20, 6);
    }

    protected void renderProgress(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int fillWidth = width * menu.progressPercent() / 100;
        guiGraphics.fill(x, y, x + width, y + height, PROGRESS_BACKGROUND_COLOR);
        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + height, PROGRESS_FILL_COLOR);
        }
    }

    protected void drawClippedStatus(GuiGraphics guiGraphics, Component status, int x, int y, int maxWidth) {
        String text = status.getString();
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, maxWidth);
        }
        guiGraphics.drawString(font, text, x, y, MUTED_TEXT_COLOR, false);
    }
}
