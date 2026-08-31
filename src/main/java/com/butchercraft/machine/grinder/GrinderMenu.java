package com.butchercraft.machine.grinder;

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

public final class GrinderMenu extends ProcessingWorkstationMenu implements MachineRunMenuView {
    private static final int GRINDER_SLOT_VERTICAL_OFFSET = 5;

    public static final int START_BUTTON = 0;
    public static final int STOP_BUTTON = 1;
    public static final int RESUME_BUTTON = 2;
    public static final int RUN_DATA_COUNT = 6;

    private final ContainerData runData;
    private final GrinderBlockEntity blockEntity;

    public GrinderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.GRINDER.get(), containerId, playerInventory, extraData, ModBlocks.GRINDER.get(),
                GrinderWorkstation.capability(), GrinderWorkstation.slotCapacityPolicy());
        this.runData = new SimpleContainerData(RUN_DATA_COUNT);
        this.blockEntity = null;
        addDataSlots(runData);
    }

    public GrinderMenu(int containerId, Inventory playerInventory, GrinderBlockEntity blockEntity) {
        super(ModMenuTypes.GRINDER.get(), containerId, playerInventory, blockEntity, ModBlocks.GRINDER.get());
        this.runData = blockEntity.runMenuData();
        this.blockEntity = blockEntity;
        addDataSlots(runData);
    }

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

    public boolean activeChild() {
        return runData.get(2) != 0;
    }

    public int runGeneration() {
        return runData.get(3);
    }

    public int completedChildren() {
        return runData.get(4);
    }

    public int nextChildSequence() {
        return runData.get(5);
    }

    public Component machineStatusComponent() {
        return Component.translatable(machineStatusKey(machineOperatingState()));
    }

    public Component cycleStatusComponent() {
        return Component.translatable(cycleStatusKey(machineOperatingState(), activeChild()));
    }

    public int cycleProgressPercent() {
        return cycleProgressPercent(machineOperatingState(), activeChild(), progressPercent());
    }

    @Override
    public Component startControlLabel() {
        return Component.translatable("screen.butchercraft.grinder.start");
    }

    @Override
    public Component stopControlLabel() {
        return Component.translatable("screen.butchercraft.grinder.stop");
    }

    @Override
    public Component resumeControlLabel() {
        return Component.translatable("screen.butchercraft.grinder.resume");
    }

    @Override public int startButtonId() { return START_BUTTON; }
    @Override public int stopButtonId() { return STOP_BUTTON; }
    @Override public int resumeButtonId() { return RESUME_BUTTON; }

    @Override
    public int workstationSlotY(int slot) {
        return super.workstationSlotY(slot) + GRINDER_SLOT_VERTICAL_OFFSET;
    }

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
        return MachineRunPresentation.machineStatusKey("grinder", state);
    }

    static String cycleStatusKey(MachineOperatingState state, boolean activeChild) {
        return MachineRunPresentation.cycleStatusKey("grinder", state, activeChild);
    }

    static int cycleProgressPercent(MachineOperatingState state, boolean activeChild, int controllerProgressPercent) {
        return MachineRunPresentation.cycleProgressPercent(state, activeChild, controllerProgressPercent);
    }
}
