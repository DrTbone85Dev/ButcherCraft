package com.butchercraft.machine.pattyformer;

import com.butchercraft.integration.machine.MachineRunPresentation;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.menu.MachineRunMenuView;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.world.execution.MachineRunLifecycle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public final class PattyFormerMenu extends ProcessingWorkstationMenu implements MachineRunMenuView {
    public static final int START_BUTTON = 0;
    public static final int STOP_BUTTON = 1;
    public static final int RESUME_BUTTON = 2;
    public static final int RUN_DATA_COUNT = 6;

    private final ContainerData runData;
    private final PattyFormerBlockEntity blockEntity;

    public PattyFormerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(
                ModMenuTypes.PATTY_FORMER.get(),
                containerId,
                playerInventory,
                extraData,
                ModBlocks.PATTY_FORMER.get(),
                PattyFormerWorkstation.capability(),
                PattyFormerWorkstation.slotCapacityPolicy()
        );
        this.runData = new SimpleContainerData(RUN_DATA_COUNT);
        this.blockEntity = null;
        addDataSlots(runData);
    }

    public PattyFormerMenu(int containerId, Inventory playerInventory, PattyFormerBlockEntity blockEntity) {
        super(ModMenuTypes.PATTY_FORMER.get(), containerId, playerInventory, blockEntity,
                ModBlocks.PATTY_FORMER.get());
        this.runData = blockEntity.runMenuData();
        this.blockEntity = blockEntity;
        addDataSlots(runData);
    }

    @Override
    public MachineOperatingState machineOperatingState() {
        int ordinal = runData.get(0);
        MachineOperatingState[] values = MachineOperatingState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MachineOperatingState.RECOVERY_REQUIRED;
    }

    public java.util.Optional<MachineRunLifecycle> runLifecycle() {
        int ordinal = runData.get(1);
        MachineRunLifecycle[] values = MachineRunLifecycle.values();
        return ordinal >= 0 && ordinal < values.length ? java.util.Optional.of(values[ordinal]) : java.util.Optional.empty();
    }

    @Override
    public boolean activeChild() {
        return runData.get(2) != 0;
    }

    public int runGeneration() { return runData.get(3); }
    public int completedChildren() { return runData.get(4); }
    public int nextChildSequence() { return runData.get(5); }

    @Override
    public Component machineStatusComponent() {
        return Component.translatable(machineStatusKey(machineOperatingState()));
    }

    @Override
    public Component cycleStatusComponent() {
        return Component.translatable(cycleStatusKey(machineOperatingState(), activeChild()));
    }

    @Override
    public int cycleProgressPercent() {
        return cycleProgressPercent(machineOperatingState(), activeChild(), progressPercent());
    }

    @Override
    public Component startControlLabel() {
        return Component.translatable("screen.butchercraft.patty_former.start");
    }

    @Override
    public Component stopControlLabel() {
        return Component.translatable("screen.butchercraft.patty_former.stop");
    }

    @Override
    public Component resumeControlLabel() {
        return Component.translatable("screen.butchercraft.patty_former.resume");
    }

    @Override public int startButtonId() { return START_BUTTON; }
    @Override public int stopButtonId() { return STOP_BUTTON; }
    @Override public int resumeButtonId() { return RESUME_BUTTON; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null || player.level().isClientSide) return false;
        var result = switch (id) {
            case START_BUTTON -> blockEntity.startRun();
            case STOP_BUTTON -> blockEntity.stopRun();
            case RESUME_BUTTON -> blockEntity.resumeRun();
            default -> null;
        };
        if (result == null) return false;
        player.displayClientMessage(Component.literal(result.detail()), false);
        return result.accepted();
    }

    static String machineStatusKey(MachineOperatingState state) {
        return MachineRunPresentation.machineStatusKey("patty_former", state);
    }

    static String cycleStatusKey(MachineOperatingState state, boolean activeChild) {
        return MachineRunPresentation.cycleStatusKey("patty_former", state, activeChild);
    }

    static int cycleProgressPercent(MachineOperatingState state, boolean activeChild, int controllerProgressPercent) {
        return MachineRunPresentation.cycleProgressPercent(state, activeChild, controllerProgressPercent);
    }
}
