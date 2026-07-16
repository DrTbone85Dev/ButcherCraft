package com.butchercraft.client.screen;

import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

abstract class AbstractProcessingWorkstationScreen<T extends ProcessingWorkstationMenu> extends AbstractContainerScreen<T> {
    private static final int BACKGROUND_COLOR = 0xFF2B3036;
    private static final int PANEL_COLOR = 0xFF3A4149;
    private static final int SLOT_COLOR = 0xFF171A1E;
    private static final int SLOT_BORDER_COLOR = 0xFF68717A;
    private static final int PROGRESS_BACKGROUND_COLOR = 0xFF1D2227;
    private static final int PROGRESS_FILL_COLOR = 0xFF70B87A;
    private static final int TEXT_COLOR = 0xFFE6E8EA;
    private static final int MUTED_TEXT_COLOR = 0xFFB8C0C8;

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
        renderSlotFrame(guiGraphics, left + 25, top + 34);
        for (int outputIndex = 0; outputIndex < menu.outputSlotCount(); outputIndex++) {
            int column = outputIndex % 4;
            int row = outputIndex / 4;
            renderSlotFrame(guiGraphics, left + 85 + column * 18, top + 25 + row * 18);
        }
        renderProgress(guiGraphics, left + 52, top + 41);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        drawClippedStatus(guiGraphics, menu.statusComponent(), 8, 66, 160);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    private void renderSlotFrame(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 18, y + 18, SLOT_BORDER_COLOR);
        guiGraphics.fill(x, y, x + 17, y + 17, SLOT_COLOR);
    }

    private void renderProgress(GuiGraphics guiGraphics, int x, int y) {
        int width = 20;
        int fillWidth = width * menu.progressPercent() / 100;
        guiGraphics.fill(x, y, x + width, y + 6, PROGRESS_BACKGROUND_COLOR);
        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + 6, PROGRESS_FILL_COLOR);
        }
    }

    private void drawClippedStatus(GuiGraphics guiGraphics, Component status, int x, int y, int maxWidth) {
        String text = status.getString();
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, maxWidth);
        }
        guiGraphics.drawString(font, text, x, y, MUTED_TEXT_COLOR, false);
    }
}
