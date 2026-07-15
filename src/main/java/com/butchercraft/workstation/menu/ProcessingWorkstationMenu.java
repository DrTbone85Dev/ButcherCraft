package com.butchercraft.workstation.menu;

import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.block.ProcessingWorkstationBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class ProcessingWorkstationMenu extends AbstractContainerMenu {
    private static final int WORKSTATION_SLOT_COUNT = 2;
    private static final int PLAYER_INVENTORY_START = WORKSTATION_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    private final WorkstationInventory inventory;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public ProcessingWorkstationMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf ignoredExtraData) {
        this(containerId, playerInventory, clientInventory(), new SimpleContainerData(4), ContainerLevelAccess.NULL);
    }

    public ProcessingWorkstationMenu(int containerId, Inventory playerInventory, ProcessingWorkstationBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.inventory(), blockEntity.menuData(), ContainerLevelAccess.create(
                blockEntity.getLevel(),
                blockEntity.getBlockPos()
        ));
    }

    private ProcessingWorkstationMenu(
            int containerId,
            Inventory playerInventory,
            WorkstationInventory inventory,
            ContainerData data,
            ContainerLevelAccess access
    ) {
        super(ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.get(), containerId);
        this.inventory = inventory;
        this.data = data;
        this.access = access;

        addSlot(new SlotItemHandler(inventory, WorkstationInventory.INPUT_SLOT, 56, 35));
        addSlot(new OutputSlot(inventory, 116, 35));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public WorkstationState workstationState() {
        int ordinal = data.get(0);
        WorkstationState[] values = WorkstationState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : WorkstationState.ERROR;
    }

    public int elapsedTicks() {
        return data.get(1);
    }

    public int totalTicks() {
        return data.get(2);
    }

    public int progressPercent() {
        int total = totalTicks();
        return total <= 0 ? 0 : Math.min(100, elapsedTicks() * 100 / total);
    }

    public WorkstationFailureCode lastFailureCode() {
        int ordinal = data.get(3);
        WorkstationFailureCode[] values = WorkstationFailureCode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack moved = original.copy();
        if (index < WORKSTATION_SLOT_COUNT) {
            if (!moveItemStackTo(original, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(original, WorkstationInventory.INPUT_SLOT, WorkstationInventory.INPUT_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION.get());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    private static WorkstationInventory clientInventory() {
        WorkstationInventory inventory = new WorkstationInventory(() -> {});
        inventory.setOutputExtractionAllowed(() -> true);
        return inventory;
    }

    private static final class OutputSlot extends SlotItemHandler {
        private OutputSlot(WorkstationInventory inventory, int x, int y) {
            super(inventory, WorkstationInventory.OUTPUT_SLOT, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
