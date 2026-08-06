package com.butchercraft.workstation.block;

import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointSchema;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointProjection;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.endpoint.runtime.WorkstationTransferEndpoint;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared block-entity foundation for workstation blocks that own a bounded inventory and menu.
 */
public abstract class AbstractInventoryWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private static final String INVENTORY_TAG = "Inventory";
    private static final String ENDPOINT_PROJECTION_TAG = "TransferEndpointProjection";

    private final WorkstationInventory inventory;
    private final WorkstationCapability capability;
    private final ContainerData menuData = new SimpleContainerData(4);
    private WorkstationEndpointProjection endpointProjection = WorkstationEndpointProjection.unbound(0L);

    protected AbstractInventoryWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability
    ) {
        super(type, pos, blockState);
        this.capability = Objects.requireNonNull(capability, "capability");
        this.inventory = new WorkstationInventory(capability, this::handleInventoryChanged);
        inventory.setTransferLocked(slot -> endpointProjection.preparedEffectId().isPresent()
                && endpointProjection.preparedSlotIndex() == slot);
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
        CompoundTag endpointTag = new CompoundTag();
        endpointTag.putInt("SchemaVersion", WorkstationEndpointSchema.CURRENT_VERSION);
        endpointProjection.instanceId().ifPresent(value -> endpointTag.putString("InstanceIdentity", value.value()));
        endpointTag.putLong("InstanceGeneration", endpointProjection.instanceGeneration());
        endpointTag.putLong("InventoryRevision", endpointProjection.inventoryRevision());
        endpointTag.putLong("EndpointEffectRevision", endpointProjection.endpointEffectRevision());
        endpointTag.putLong("LastAppliedJournalSequence", endpointProjection.lastAppliedJournalSequence());
        endpointProjection.lastEffectId().ifPresent(value -> endpointTag.putString("LastEffectIdentity", value.value()));
        endpointProjection.lastOwnerResultIdentity().ifPresent(
                value -> endpointTag.putString("LastOwnerResultIdentity", value)
        );
        endpointProjection.preparedEffectId().ifPresent(value -> endpointTag.putString("PreparedEffectIdentity", value.value()));
        endpointTag.putInt("PreparedSlotIndex", endpointProjection.preparedSlotIndex());
        endpointTag.putLong("PreparedInventoryRevision", endpointProjection.preparedInventoryRevision());
        tag.put(ENDPOINT_PROJECTION_TAG, endpointTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(INVENTORY_TAG, Tag.TAG_COMPOUND)) {
            inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_TAG));
        }
        if (tag.contains(ENDPOINT_PROJECTION_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag endpointTag = tag.getCompound(ENDPOINT_PROJECTION_TAG);
            int schemaVersion = endpointTag.getInt("SchemaVersion");
            if (schemaVersion != WorkstationEndpointSchema.CURRENT_VERSION) {
                throw new IllegalStateException(
                        "Unsupported Workstation endpoint projection schema version: " + schemaVersion
                );
            }
            Optional<WorkstationInstanceId> instanceId = endpointTag.contains("InstanceIdentity", Tag.TAG_STRING)
                    ? Optional.of(new WorkstationInstanceId(endpointTag.getString("InstanceIdentity")))
                    : Optional.empty();
            Optional<WorkstationEndpointEffectId> lastEffect = endpointTag.contains(
                    "LastEffectIdentity",
                    Tag.TAG_STRING
            ) ? Optional.of(new WorkstationEndpointEffectId(endpointTag.getString("LastEffectIdentity")))
                    : Optional.empty();
            Optional<String> lastResult = endpointTag.contains("LastOwnerResultIdentity", Tag.TAG_STRING)
                    ? Optional.of(endpointTag.getString("LastOwnerResultIdentity"))
                    : Optional.empty();
            Optional<WorkstationEndpointEffectId> preparedEffect = endpointTag.contains(
                    "PreparedEffectIdentity",
                    Tag.TAG_STRING
            ) ? Optional.of(new WorkstationEndpointEffectId(endpointTag.getString("PreparedEffectIdentity")))
                    : Optional.empty();
            endpointProjection = new WorkstationEndpointProjection(
                    instanceId,
                    endpointTag.getLong("InstanceGeneration"),
                    endpointTag.getLong("InventoryRevision"),
                    endpointTag.getLong("EndpointEffectRevision"),
                    endpointTag.getLong("LastAppliedJournalSequence"),
                    lastEffect,
                    lastResult,
                    preparedEffect,
                    preparedEffect.isPresent() ? endpointTag.getInt("PreparedSlotIndex") : -1,
                    preparedEffect.isPresent() ? endpointTag.getLong("PreparedInventoryRevision") : 0L
            );
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && this instanceof WorkstationTransferEndpoint) {
            WorkstationEndpointService.INSTANCE.reconcileLoadedEndpoint(serverLevel, worldPosition);
        }
    }

    protected final WorkstationEndpointProjection endpointProjectionView() {
        return endpointProjection;
    }

    protected final ItemStack endpointStackSnapshotView(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventory.totalSlotCount()) {
            throw new IllegalArgumentException("Endpoint slot is outside workstation inventory range");
        }
        return inventory.getStackInSlot(slotIndex).copy();
    }

    protected final boolean endpointAcceptsView(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactStack
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(exactStack, "exactStack");
        if (slotIndex < 0 || slotIndex >= inventory.totalSlotCount() || exactStack.isEmpty()) return false;
        return switch (kind) {
            case SOURCE_WITHDRAWAL -> inventory.isInputSlot(slotIndex)
                    && ItemStack.isSameItemSameComponents(inventory.getStackInSlot(slotIndex), exactStack)
                    && inventory.getStackInSlot(slotIndex).getCount() == exactStack.getCount();
            case DESTINATION_DEPOSIT, SOURCE_RETURN -> inventory.isInputSlot(slotIndex)
                    && inventory.getStackInSlot(slotIndex).isEmpty()
                    && inventory.isItemValid(slotIndex, exactStack);
        };
    }

    protected final void bindEndpointInstanceView(WorkstationInstanceId instanceId, long generation) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (generation <= 0L) throw new IllegalArgumentException("Endpoint generation must be positive");
        if (endpointProjection.instanceId().isPresent()
                && (!endpointProjection.instanceId().orElseThrow().equals(instanceId)
                || endpointProjection.instanceGeneration() != generation)) {
            throw new IllegalStateException("Workstation endpoint projection is already bound to another instance");
        }
        endpointProjection = new WorkstationEndpointProjection(
                Optional.of(instanceId),
                generation,
                endpointProjection.inventoryRevision(),
                endpointProjection.endpointEffectRevision(),
                endpointProjection.lastAppliedJournalSequence(),
                endpointProjection.lastEffectId(),
                endpointProjection.lastOwnerResultIdentity(),
                endpointProjection.preparedEffectId(),
                endpointProjection.preparedSlotIndex(),
                endpointProjection.preparedInventoryRevision()
        );
        setChanged();
    }

    protected final void applyCommittedEndpointEffectView(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactStack,
            long expectedInventoryRevision,
            long postInventoryRevision,
            long endpointEffectRevision,
            long journalSequence,
            WorkstationEndpointEffectId effectId,
            String ownerResultIdentity
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(exactStack, "exactStack");
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(ownerResultIdentity, "ownerResultIdentity");
        if (endpointProjection.inventoryRevision() != expectedInventoryRevision) {
            throw new IllegalStateException("Workstation inventory freshness changed before committed endpoint effect");
        }
        if (endpointProjection.preparedEffectId().filter(effectId::equals).isEmpty()
                || endpointProjection.preparedInventoryRevision() != expectedInventoryRevision) {
            throw new IllegalStateException("Workstation endpoint effect does not own the prepared slot lock");
        }
        if (journalSequence <= endpointProjection.lastAppliedJournalSequence()) {
            throw new IllegalArgumentException("Endpoint journal sequence must advance monotonically");
        }
        if (endpointEffectRevision != Math.addExact(endpointProjection.endpointEffectRevision(), 1L)
                || postInventoryRevision != Math.addExact(expectedInventoryRevision, 1L)) {
            throw new IllegalArgumentException("Endpoint effect revisions are not the next Workstation-owned revisions");
        }
        if (!endpointAcceptsView(kind, slotIndex, exactStack)) {
            throw new IllegalStateException("Live Workstation projection no longer accepts committed endpoint effect");
        }
        switch (kind) {
            case SOURCE_WITHDRAWAL -> inventory.clearInputSlotsInternal(java.util.List.of(slotIndex));
            case DESTINATION_DEPOSIT, SOURCE_RETURN ->
                    inventory.setInputInternal(slotIndex - inventory.firstInputSlot(), exactStack.copy());
        }
        if (endpointProjection.inventoryRevision() != postInventoryRevision) {
            throw new IllegalStateException("Workstation inventory mutation did not publish the committed revision");
        }
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(),
                endpointProjection.instanceGeneration(),
                endpointProjection.inventoryRevision(),
                endpointEffectRevision,
                journalSequence,
                Optional.of(effectId),
                Optional.of(ownerResultIdentity),
                Optional.empty(),
                -1,
                0L
        );
        setChanged();
    }

    private void handleInventoryChanged() {
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(),
                endpointProjection.instanceGeneration(),
                Math.addExact(endpointProjection.inventoryRevision(), 1L),
                endpointProjection.endpointEffectRevision(),
                endpointProjection.lastAppliedJournalSequence(),
                endpointProjection.lastEffectId(),
                endpointProjection.lastOwnerResultIdentity(),
                endpointProjection.preparedEffectId(),
                endpointProjection.preparedSlotIndex(),
                endpointProjection.preparedInventoryRevision()
        );
        onInventoryChanged();
    }

    protected final void lockPreparedEndpointEffectView(
            WorkstationEndpointEffectId effectId,
            int slotIndex,
            long expectedInventoryRevision
    ) {
        Objects.requireNonNull(effectId, "effectId");
        if (endpointProjection.preparedEffectId().isPresent()
                && !endpointProjection.preparedEffectId().orElseThrow().equals(effectId)) {
            throw new IllegalStateException("Workstation endpoint slot is locked by another effect");
        }
        if (endpointProjection.inventoryRevision() != expectedInventoryRevision) {
            throw new IllegalStateException("Workstation inventory freshness changed before endpoint lock");
        }
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(),
                endpointProjection.instanceGeneration(),
                endpointProjection.inventoryRevision(),
                endpointProjection.endpointEffectRevision(),
                endpointProjection.lastAppliedJournalSequence(),
                endpointProjection.lastEffectId(),
                endpointProjection.lastOwnerResultIdentity(),
                Optional.of(effectId),
                slotIndex,
                expectedInventoryRevision
        );
        setChanged();
    }

    protected final void releasePreparedEndpointEffectView(WorkstationEndpointEffectId effectId) {
        Objects.requireNonNull(effectId, "effectId");
        if (endpointProjection.preparedEffectId().filter(effectId::equals).isEmpty()) return;
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(),
                endpointProjection.instanceGeneration(),
                endpointProjection.inventoryRevision(),
                endpointProjection.endpointEffectRevision(),
                endpointProjection.lastAppliedJournalSequence(),
                endpointProjection.lastEffectId(),
                endpointProjection.lastOwnerResultIdentity(),
                Optional.empty(),
                -1,
                0L
        );
        setChanged();
    }
}
