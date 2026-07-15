package com.butchercraft.workstation.block;

import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.workstation.DevelopmentProductItemMapping;
import com.butchercraft.workstation.DevelopmentWorkstationFixtures;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class ProcessingWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private static final String INVENTORY_TAG = "Inventory";
    private static final String CONTROLLER_TAG = "Controller";

    private final WorkstationInventory inventory;
    private final WorkstationProcessingController controller;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> controller.state().ordinal();
                case 1 -> controller.elapsedTicks();
                case 2 -> controller.totalTicks();
                case 3 -> controller.lastFailure().map(failure -> failure.code().ordinal()).orElse(-1);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-owned view data; client writes are ignored.
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public ProcessingWorkstationBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.DEVELOPMENT_PROCESSING_WORKSTATION.get(), pos, blockState);
        this.inventory = new WorkstationInventory(this::onInventoryChanged);
        this.controller = new WorkstationProcessingController(
                inventory,
                DevelopmentWorkstationFixtures.capability(),
                new WorkstationOperationResolver(),
                DevelopmentProductItemMapping.fixtureMapping(),
                this::markChanged
        );
        inventory.setInputLocked(controller::inputLocked);
        inventory.setOutputExtractionAllowed(controller::outputExtractionAllowed);
    }

    public WorkstationInventory inventory() {
        return inventory;
    }

    public WorkstationState workstationState() {
        return controller.state();
    }

    public Optional<WorkstationFailure> lastFailure() {
        return controller.lastFailure();
    }

    public ContainerData menuData() {
        return menuData;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessingWorkstationBlockEntity blockEntity) {
        if (!level.isClientSide) {
            blockEntity.controller.serverTick(level.registryAccess());
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        controller.cancelPreservingInput();
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.input());
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.output());
        inventory.setInputInternal(net.minecraft.world.item.ItemStack.EMPTY);
        inventory.setOutputInternal(net.minecraft.world.item.ItemStack.EMPTY);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.butchercraft.development_processing_workstation");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ProcessingWorkstationMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(INVENTORY_TAG, inventory.serializeNBT(registries));
        CompoundTag controllerTag = new CompoundTag();
        controller.saveAdditional(controllerTag, registries);
        tag.put(CONTROLLER_TAG, controllerTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(INVENTORY_TAG, Tag.TAG_COMPOUND)) {
            inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_TAG));
        }
        if (tag.contains(CONTROLLER_TAG, Tag.TAG_COMPOUND)) {
            controller.loadAdditional(tag.getCompound(CONTROLLER_TAG), registries);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void onInventoryChanged() {
        controller.onInventoryChanged();
    }

    private void markChanged() {
        setChanged();
    }
}
