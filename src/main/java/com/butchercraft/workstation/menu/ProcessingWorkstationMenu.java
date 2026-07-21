package com.butchercraft.workstation.menu;

import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.DevelopmentWorkstationFixtures;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.block.AbstractInventoryWorkstationBlockEntity;
import com.butchercraft.workstation.block.ProcessingWorkstationBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ProcessingWorkstationMenu extends AbstractContainerMenu {
    private final WorkstationInventory inventory;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final Block validBlock;
    private final int workstationSlotCount;
    private final int playerInventoryStart;
    private final int playerInventoryEnd;
    private final int hotbarEnd;

    public ProcessingWorkstationMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf ignoredExtraData) {
        this(
                ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.get(),
                containerId,
                playerInventory,
                ignoredExtraData,
                ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION.get(),
                DevelopmentWorkstationFixtures.capability()
        );
    }

    public ProcessingWorkstationMenu(int containerId, Inventory playerInventory, ProcessingWorkstationBlockEntity blockEntity) {
        this(
                ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.get(),
                containerId,
                playerInventory,
                blockEntity,
                ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION.get()
        );
    }

    protected ProcessingWorkstationMenu(
            MenuType<?> menuType,
            int containerId,
            Inventory playerInventory,
            RegistryFriendlyByteBuf ignoredExtraData,
            Block validBlock,
            WorkstationCapability capability
    ) {
        this(menuType, containerId, playerInventory, clientInventory(capability), new SimpleContainerData(4), ContainerLevelAccess.NULL, validBlock);
    }

    protected ProcessingWorkstationMenu(
            MenuType<?> menuType,
            int containerId,
            Inventory playerInventory,
            AbstractInventoryWorkstationBlockEntity blockEntity,
            Block validBlock
    ) {
        this(menuType, containerId, playerInventory, blockEntity.inventory(), blockEntity.menuData(), ContainerLevelAccess.create(
                blockEntity.getLevel(),
                blockEntity.getBlockPos()
        ), validBlock);
    }

    private ProcessingWorkstationMenu(
            MenuType<?> menuType,
            int containerId,
            Inventory playerInventory,
            WorkstationInventory inventory,
            ContainerData data,
            ContainerLevelAccess access,
            Block validBlock
    ) {
        super(menuType, containerId);
        this.inventory = inventory;
        this.data = data;
        this.access = access;
        this.validBlock = validBlock;
        this.workstationSlotCount = inventory.totalSlotCount();
        this.playerInventoryStart = workstationSlotCount;
        this.playerInventoryEnd = playerInventoryStart + 27;
        this.hotbarEnd = playerInventoryEnd + 9;

        for (int inputIndex = 0; inputIndex < inventory.inputSlotCount(); inputIndex++) {
            int slot = inventory.firstInputSlot() + inputIndex;
            addSlot(new SlotItemHandler(inventory, slot, workstationSlotX(slot), workstationSlotY(slot)));
        }
        for (int outputIndex = 0; outputIndex < inventory.outputSlotCount(); outputIndex++) {
            int slot = inventory.firstOutputSlot() + outputIndex;
            addSlot(new OutputSlot(inventory, slot, workstationSlotX(slot), workstationSlotY(slot)));
        }
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public int workstationSlotCount() {
        return workstationSlotCount;
    }

    public int playerInventoryStart() {
        return playerInventoryStart;
    }

    public int outputSlotCount() {
        return inventory.outputSlotCount();
    }

    public int inputSlotCount() {
        return inventory.inputSlotCount();
    }

    public int firstOutputSlot() {
        return inventory.firstOutputSlot();
    }

    public int workstationSlotX(int slot) {
        if (inventory.inputSlotCount() == 3 && inventory.outputSlotCount() == 1) {
            if (inventory.isInputSlot(slot)) {
                return 32 + slot * 30;
            }
            return 62;
        }
        if (inventory.isInputSlot(slot)) {
            return 26;
        }

        int outputIndex = slot - inventory.firstOutputSlot();
        int column = outputIndex % 4;
        return 86 + column * 18;
    }

    public int workstationSlotY(int slot) {
        if (inventory.inputSlotCount() == 3 && inventory.outputSlotCount() == 1) {
            return inventory.isInputSlot(slot) ? 25 : 55;
        }
        if (inventory.isInputSlot(slot)) {
            return 35;
        }

        int outputIndex = slot - inventory.firstOutputSlot();
        int row = outputIndex / 4;
        return 26 + row * 18;
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

    public Component statusComponent() {
        WorkstationFailureCode failureCode = lastFailureCode();
        if (failureCode != null) {
            return Component.translatable(failureCode.messageKey());
        }
        return Component.translatable(workstationState().messageKey());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (inventory.isInputSlot(index) && inventory.isInputLocked()) {
            return ItemStack.EMPTY;
        }
        if (inventory.isOutputSlot(index) && !inventory.isOutputExtractionAllowed()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack moved = original.copy();
        if (index < workstationSlotCount) {
            if (!moveItemStackTo(original, playerInventoryStart, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(original, inventory.firstInputSlot(), inventory.firstOutputSlot(), false)) {
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
        return stillValid(access, player, validBlock);
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

    private static WorkstationInventory clientInventory(WorkstationCapability capability) {
        WorkstationInventory inventory = new WorkstationInventory(capability, () -> {});
        inventory.setOutputExtractionAllowed(() -> true);
        return inventory;
    }

    private static final class OutputSlot extends SlotItemHandler {
        private OutputSlot(WorkstationInventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
