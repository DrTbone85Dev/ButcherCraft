package com.butchercraft.client.screen;

import com.butchercraft.workstation.menu.MachineRunMenuView;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import com.butchercraft.workstation.operation.MachineOperatingState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

abstract class AbstractMachineRunScreen<T extends ProcessingWorkstationMenu & MachineRunMenuView>
        extends AbstractProcessingWorkstationScreen<T> {
    private static final int STATUS_X = 8;
    private static final int MACHINE_STATUS_Y = 18;
    private static final int CYCLE_STATUS_Y = 62;
    private static final int CYCLE_STATUS_WIDTH = 128;
    private static final int CONTROL_X = 144;
    private static final int PRIMARY_CONTROL_Y = 32;
    private static final int SECONDARY_CONTROL_Y = 52;
    private static final int CONTROL_WIDTH = 68;
    private static final int CONTROL_HEIGHT = 18;

    private Button primaryButton;
    private Button stopAfterRestartButton;

    AbstractMachineRunScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 220;
    }

    @Override
    protected void init() {
        super.init();
        primaryButton = Button.builder(menu.startControlLabel(), button -> sendControl(controlButtonId()))
                .bounds(leftPos + CONTROL_X, topPos + PRIMARY_CONTROL_Y, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build();
        stopAfterRestartButton = Button.builder(menu.stopControlLabel(), button -> sendControl(menu.stopButtonId()))
                .bounds(leftPos + CONTROL_X, topPos + SECONDARY_CONTROL_Y, CONTROL_WIDTH, CONTROL_HEIGHT)
                .build();
        addRenderableWidget(primaryButton);
        addRenderableWidget(stopAfterRestartButton);
        updateControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControls();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        guiGraphics.drawString(font, menu.machineStatusComponent(), STATUS_X, MACHINE_STATUS_Y, TEXT_COLOR, false);
        drawClippedStatus(guiGraphics, menu.cycleStatusComponent(), STATUS_X, CYCLE_STATUS_Y, CYCLE_STATUS_WIDTH);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    @Override
    protected void renderProgress(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int fillWidth = width * menu.cycleProgressPercent() / 100;
        guiGraphics.fill(x, y, x + width, y + height, PROGRESS_BACKGROUND_COLOR);
        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + height, PROGRESS_FILL_COLOR);
        }
    }

    private void updateControls() {
        if (primaryButton == null || stopAfterRestartButton == null) return;
        MachineOperatingState state = menu.machineOperatingState();
        primaryButton.visible = state != MachineOperatingState.STARTING
                && state != MachineOperatingState.STOPPING
                && state != MachineOperatingState.RECOVERY_REQUIRED
                && state != MachineOperatingState.FAULTED;
        if (state == MachineOperatingState.OFF) {
            primaryButton.setMessage(menu.startControlLabel());
            primaryButton.active = primaryButton.visible;
        } else if (state == MachineOperatingState.RESTART_REQUIRED) {
            primaryButton.setMessage(menu.resumeControlLabel());
            primaryButton.active = primaryButton.visible && !menu.activeChild();
        } else {
            primaryButton.setMessage(menu.stopControlLabel());
            primaryButton.active = primaryButton.visible;
        }
        stopAfterRestartButton.visible = state == MachineOperatingState.RESTART_REQUIRED;
        stopAfterRestartButton.active = stopAfterRestartButton.visible;
    }

    private int controlButtonId() {
        return switch (menu.machineOperatingState()) {
            case OFF -> menu.startButtonId();
            case RESTART_REQUIRED -> menu.resumeButtonId();
            default -> menu.stopButtonId();
        };
    }

    private void sendControl(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
