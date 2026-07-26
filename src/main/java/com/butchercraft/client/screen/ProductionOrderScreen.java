package com.butchercraft.client.screen;

import com.butchercraft.productioncontrol.ProductionOrderControl;
import com.butchercraft.productioncontrol.ProductionOrderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ProductionOrderScreen extends AbstractContainerScreen<ProductionOrderMenu> {
    private static final int BACKGROUND_COLOR = 0xFF263238;
    private static final int PANEL_COLOR = 0xFF37474F;
    private static final int LINE_COLOR = 0xFF78909C;
    private static final int PROGRESS_BACKGROUND_COLOR = 0xFF1B2529;
    private static final int GRINDER_PROGRESS_COLOR = 0xFF74A57F;
    private static final int PATTY_PROGRESS_COLOR = 0xFFD8A657;
    private static final int TEXT_COLOR = 0xFFECEFF1;
    private static final int MUTED_TEXT_COLOR = 0xFFB0BEC5;
    private static final int WARNING_TEXT_COLOR = 0xFFFFCC80;

    private Button cancelButton;

    public ProductionOrderScreen(ProductionOrderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 224;
        imageHeight = 176;
        titleLabelX = 10;
        titleLabelY = 8;
        inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        cancelButton = Button.builder(
                Component.translatable("screen.butchercraft.production_order.cancel"),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId,
                                ProductionOrderControl.cancelButtonId()
                        );
                    }
                }
        ).bounds(leftPos + imageWidth - 76, topPos + imageHeight - 28, 64, 20).build();
        addRenderableWidget(cancelButton);
        updateCancelButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateCancelButton();
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
        guiGraphics.fill(left + 8, top + 22, left + imageWidth - 8, top + 150, PANEL_COLOR);
        guiGraphics.hLine(left + 20, left + imageWidth - 20, top + 72, LINE_COLOR);
        renderProgress(guiGraphics, left + 48, top + 52, menu.grinderProgressPercent(), GRINDER_PROGRESS_COLOR);
        renderProgress(guiGraphics, left + 48, top + 118, menu.pattyFormerProgressPercent(), PATTY_PROGRESS_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        drawClipped(guiGraphics, Component.translatable("screen.butchercraft.production_order.chain"), 10, 24, 200, MUTED_TEXT_COLOR);
        drawClipped(guiGraphics, menu.nextActionComponent(), 10, 38, 200, WARNING_TEXT_COLOR);

        drawClipped(guiGraphics, Component.translatable("screen.butchercraft.production_order.step.grinder"), 10, 82, 88, TEXT_COLOR);
        drawClipped(guiGraphics, menu.grinderStatusComponent(), 98, 82, 112, MUTED_TEXT_COLOR);
        drawClipped(guiGraphics, Component.translatable("screen.butchercraft.production_order.step.transfer"), 10, 96, 200, MUTED_TEXT_COLOR);
        drawClipped(guiGraphics, Component.translatable("screen.butchercraft.production_order.step.patty_former"), 10, 112, 88, TEXT_COLOR);
        drawClipped(guiGraphics, menu.pattyFormerStatusComponent(), 98, 112, 112, MUTED_TEXT_COLOR);
        drawClipped(guiGraphics, menu.chainStatusComponent(), 10, 134, 144, MUTED_TEXT_COLOR);
    }

    private void renderProgress(GuiGraphics guiGraphics, int x, int y, int percent, int fillColor) {
        int width = 128;
        int height = 7;
        int clamped = Math.max(0, Math.min(100, percent));
        guiGraphics.fill(x, y, x + width, y + height, PROGRESS_BACKGROUND_COLOR);
        if (clamped > 0) {
            guiGraphics.fill(x, y, x + width * clamped / 100, y + height, fillColor);
        }
    }

    private void drawClipped(GuiGraphics guiGraphics, Component component, int x, int y, int maxWidth, int color) {
        String text = component.getString();
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, maxWidth);
        }
        guiGraphics.drawString(font, text, x, y, color, false);
    }

    private void updateCancelButton() {
        if (cancelButton != null) {
            cancelButton.active = menu.canCancel();
            cancelButton.visible = menu.hasRun() && !menu.staleReference();
        }
    }
}
