package com.butchercraft.workstation.block;

import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.workstation.DevelopmentProductItemMapping;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationOperationLookup;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractProcessingWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private static final String INVENTORY_TAG = "Inventory";
    private static final String CONTROLLER_TAG = "Controller";

    private final WorkstationInventory inventory;
    private final WorkstationCapability capability;
    private final WorkstationOperationLookup resolver;
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

    protected AbstractProcessingWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping
    ) {
        super(type, pos, blockState);
        this.capability = Objects.requireNonNull(capability, "capability");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.inventory = new WorkstationInventory(this::onInventoryChanged);
        this.controller = new WorkstationProcessingController(
                inventory,
                capability,
                resolver,
                Objects.requireNonNull(outputMapping, "outputMapping"),
                this::markChanged
        );
        inventory.setInputLocked(controller::inputLocked);
        inventory.setOutputExtractionAllowed(controller::outputExtractionAllowed);
        inventory.setInputValidator(this::canAcceptInput);
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

    protected WorkstationCapability capability() {
        return capability;
    }

    public static <T extends AbstractProcessingWorkstationBlockEntity> void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            T blockEntity
    ) {
        if (!level.isClientSide) {
            blockEntity.tickController(level.registryAccess());
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        controller.cancelPreservingInput();
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.input());
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.output());
        inventory.setInputInternal(ItemStack.EMPTY);
        inventory.setOutputInternal(ItemStack.EMPTY);
    }

    @Nullable
    @Override
    public final AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return createWorkstationMenu(containerId, playerInventory, player);
    }

    @Nullable
    protected abstract AbstractContainerMenu createWorkstationMenu(int containerId, Inventory playerInventory, Player player);

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

    private boolean canAcceptInput(ItemStack stack) {
        if (stack.isEmpty() || !ProductStackAdapter.readProductData(stack).succeeded()) {
            return false;
        }
        if (level == null) {
            return true;
        }
        return resolver.resolve(level.registryAccess(), capability, stack).succeeded();
    }

    protected final void tickController(RegistryAccess registryAccess) {
        controller.serverTick(registryAccess);
    }

    private void onInventoryChanged() {
        controller.onInventoryChanged();
    }

    private void markChanged() {
        setChanged();
    }
}
