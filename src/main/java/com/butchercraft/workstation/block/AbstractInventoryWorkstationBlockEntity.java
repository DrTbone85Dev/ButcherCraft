package com.butchercraft.workstation.block;

import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Shared block-entity foundation for workstation blocks that own a bounded inventory and menu.
 */
public abstract class AbstractInventoryWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private static final String INVENTORY_TAG = "Inventory";

    private final WorkstationInventory inventory;
    private final WorkstationCapability capability;
    private final ContainerData menuData = new SimpleContainerData(4);

    protected AbstractInventoryWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability
    ) {
        super(type, pos, blockState);
        this.capability = Objects.requireNonNull(capability, "capability");
        this.inventory = new WorkstationInventory(capability, this::onInventoryChanged);
        inventory.setInputValidator(stack -> !stack.isEmpty());
        inventory.setOutputExtractionAllowed(() -> true);
    }

    public WorkstationInventory inventory() {
        return inventory;
    }

    public ContainerData menuData() {
        return menuData;
    }

    protected WorkstationCapability capability() {
        return capability;
    }

    public void dropContents(Level level, BlockPos pos) {
        beforeDropContents();
        for (ItemStack stack : inventory.inputs()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        for (ItemStack stack : inventory.outputs()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        inventory.clearAllInternal();
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(INVENTORY_TAG, Tag.TAG_COMPOUND)) {
            inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_TAG));
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

    protected void beforeDropContents() {
    }

    protected void onInventoryChanged() {
        setChanged();
    }
}
