package com.butchercraft.workstation.block;

import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationSlotCapacityPolicy;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointSchema;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointProjection;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.endpoint.runtime.StackAwareWorkstationEndpointRuntimeService;
import com.butchercraft.workstation.endpoint.runtime.StackAwareWorkstationTransferEndpoint;
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
    private int endpointProtocolVersion = WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION;
    private Optional<WorkstationEndpointEffectIdV2> stackAwarePreparedEffectId = Optional.empty();
    private Optional<WorkstationEndpointEffectIdV2> stackAwareLastEffectId = Optional.empty();
    private Optional<String> stackAwareLastOwnerResultIdentity = Optional.empty();

    protected AbstractInventoryWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability
    ) {
        this(
                type,
                pos,
                blockState,
                capability,
                WorkstationSlotCapacityPolicy.uniform(capability.inputSlots() + capability.outputSlots(), 1)
        );
    }

    protected AbstractInventoryWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability,
            WorkstationSlotCapacityPolicy slotCapacityPolicy
    ) {
        super(type, pos, blockState);
        this.capability = Objects.requireNonNull(capability, "capability");
        this.inventory = new WorkstationInventory(
                capability,
                Objects.requireNonNull(slotCapacityPolicy, "slotCapacityPolicy"),
                this::handleInventoryChanged
        );
        inventory.setTransferLocked(slot -> (endpointProjection.preparedEffectId().isPresent()
                || stackAwarePreparedEffectId.isPresent()) && endpointProjection.preparedSlotIndex() == slot);
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
        endpointTag.putInt("SchemaVersion", endpointProtocolVersion);
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
        stackAwarePreparedEffectId.ifPresent(value ->
                endpointTag.putString("StackAwarePreparedEffectIdentity", value.value()));
        stackAwareLastEffectId.ifPresent(value -> endpointTag.putString("StackAwareLastEffectIdentity", value.value()));
        stackAwareLastOwnerResultIdentity.ifPresent(
                value -> endpointTag.putString("StackAwareLastOwnerResultIdentity", value)
        );
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
            if (schemaVersion != WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION
                    && schemaVersion != WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION) {
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
            Optional<WorkstationEndpointEffectIdV2> stackAwarePrepared = endpointTag.contains(
                    "StackAwarePreparedEffectIdentity",
                    Tag.TAG_STRING
            ) ? Optional.of(new WorkstationEndpointEffectIdV2(
                    endpointTag.getString("StackAwarePreparedEffectIdentity")
            )) : Optional.empty();
            Optional<WorkstationEndpointEffectIdV2> stackAwareLast = endpointTag.contains(
                    "StackAwareLastEffectIdentity",
                    Tag.TAG_STRING
            ) ? Optional.of(new WorkstationEndpointEffectIdV2(
                    endpointTag.getString("StackAwareLastEffectIdentity")
            )) : Optional.empty();
            Optional<String> stackAwareLastResult = endpointTag.contains(
                    "StackAwareLastOwnerResultIdentity",
                    Tag.TAG_STRING
            ) ? Optional.of(endpointTag.getString("StackAwareLastOwnerResultIdentity")) : Optional.empty();
            if (schemaVersion == WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION
                    && (stackAwarePrepared.isPresent() || stackAwareLast.isPresent()
                    || stackAwareLastResult.isPresent())) {
                throw new IllegalStateException("Schema-1 endpoint projection contains schema-2 effect identity");
            }
            if (schemaVersion == WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION
                    && (preparedEffect.isPresent() || lastEffect.isPresent() || lastResult.isPresent())) {
                throw new IllegalStateException("Schema-2 endpoint projection contains schema-1 effect markers");
            }
            if (stackAwareLast.isEmpty() != stackAwareLastResult.isEmpty()) {
                throw new IllegalStateException("Schema-2 endpoint result markers must be published together");
            }
            if (stackAwareLast.isPresent() && endpointTag.getLong("LastAppliedJournalSequence") <= 0L) {
                throw new IllegalStateException("Schema-2 endpoint result marker requires a journal sequence");
            }
            endpointProtocolVersion = schemaVersion;
            stackAwarePreparedEffectId = stackAwarePrepared;
            stackAwareLastEffectId = stackAwareLast;
            stackAwareLastOwnerResultIdentity = stackAwareLastResult;
            endpointProjection = new WorkstationEndpointProjection(
                    instanceId,
                    endpointTag.getLong("InstanceGeneration"),
                    endpointTag.getLong("InventoryRevision"),
                    endpointTag.getLong("EndpointEffectRevision"),
                    endpointTag.getLong("LastAppliedJournalSequence"),
                    lastEffect,
                    lastResult,
                    preparedEffect,
                    preparedEffect.isPresent() || stackAwarePrepared.isPresent()
                            ? endpointTag.getInt("PreparedSlotIndex") : -1,
                    preparedEffect.isPresent() || stackAwarePrepared.isPresent()
                            ? endpointTag.getLong("PreparedInventoryRevision") : 0L
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
        if (level instanceof ServerLevel serverLevel && this instanceof StackAwareWorkstationTransferEndpoint) {
            StackAwareWorkstationEndpointRuntimeService.INSTANCE.reconcileLoadedEndpoint(serverLevel, worldPosition);
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
            case SOURCE_WITHDRAWAL -> (inventory.isInputSlot(slotIndex) || inventory.isOutputSlot(slotIndex))
                    && ItemStack.isSameItemSameComponents(inventory.getStackInSlot(slotIndex), exactStack)
                    && inventory.getStackInSlot(slotIndex).getCount() == exactStack.getCount();
            case DESTINATION_DEPOSIT -> inventory.isInputSlot(slotIndex)
                    && inventory.getStackInSlot(slotIndex).isEmpty()
                    && inventory.isItemValid(slotIndex, exactStack);
            case SOURCE_RETURN -> inventory.getStackInSlot(slotIndex).isEmpty()
                    && (inventory.isOutputSlot(slotIndex)
                    || inventory.isInputSlot(slotIndex) && inventory.isItemValid(slotIndex, exactStack));
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
        if (endpointProtocolVersion != WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION) {
            throw new IllegalStateException("Schema-1 endpoint effect cannot mutate a schema-2 projection");
        }
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
            case SOURCE_WITHDRAWAL -> {
                if (inventory.isInputSlot(slotIndex)) {
                    inventory.clearInputSlotsInternal(java.util.List.of(slotIndex));
                } else {
                    inventory.clearOutputSlotsInternal(java.util.List.of(slotIndex));
                }
            }
            case DESTINATION_DEPOSIT ->
                    inventory.setInputInternal(slotIndex - inventory.firstInputSlot(), exactStack.copy());
            case SOURCE_RETURN -> {
                if (inventory.isInputSlot(slotIndex)) {
                    inventory.setInputInternal(slotIndex - inventory.firstInputSlot(), exactStack.copy());
                } else {
                    inventory.setOutputInternal(slotIndex - inventory.firstOutputSlot(), exactStack.copy());
                }
            }
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
        if (endpointProtocolVersion != WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION) {
            throw new IllegalStateException("Schema-1 endpoint effect cannot lock a schema-2 projection");
        }
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

    protected final void activateStackAwareEndpointView() {
        if (endpointProtocolVersion == WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION) return;
        if (endpointProjection.preparedEffectId().isPresent()) {
            throw new IllegalStateException("Unresolved schema-1 endpoint effect blocks schema-2 activation");
        }
        endpointProtocolVersion = WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION;
        stackAwarePreparedEffectId = Optional.empty();
        stackAwareLastEffectId = Optional.empty();
        stackAwareLastOwnerResultIdentity = Optional.empty();
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(), endpointProjection.instanceGeneration(),
                endpointProjection.inventoryRevision(), endpointProjection.endpointEffectRevision(),
                endpointProjection.lastAppliedJournalSequence(), Optional.empty(), Optional.empty(),
                Optional.empty(), -1, 0L
        );
        setChanged();
    }

    protected final WorkstationInstanceId stackAwareEndpointInstanceIdView() {
        return endpointProjection.instanceId().orElseThrow(() ->
                new IllegalStateException("Workstation endpoint instance is not bound"));
    }

    protected final WorkstationEndpointKey stackAwareEndpointKeyView(String workstationTypeIdentity) {
        if (level == null) throw new IllegalStateException("Workstation endpoint is not attached to a level");
        return new WorkstationEndpointKey(
                workstationTypeIdentity,
                level.dimension().location().toString(),
                worldPosition.getX(),
                worldPosition.getY(),
                worldPosition.getZ()
        );
    }

    protected final int stackAwareEffectiveCapacityView(int slotIndex, ItemStack stack) {
        return inventory.effectiveSlotCapacity(slotIndex, stack);
    }

    protected final long stackAwareInventoryRevisionView() {
        return endpointProjection.inventoryRevision();
    }

    protected final long stackAwareEffectRevisionView() {
        return endpointProjection.endpointEffectRevision();
    }

    protected final long stackAwareLastJournalSequenceView() {
        return endpointProjection.lastAppliedJournalSequence();
    }

    protected final Optional<WorkstationEndpointEffectIdV2> stackAwarePreparedEffectIdView() {
        return stackAwarePreparedEffectId;
    }

    protected final Optional<WorkstationEndpointEffectIdV2> stackAwareLastEffectIdView() {
        return stackAwareLastEffectId;
    }

    protected final Optional<String> stackAwareLastOwnerResultIdentityView() {
        return stackAwareLastOwnerResultIdentity;
    }

    protected final boolean stackAwareAcceptsCandidateView(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactPreStack,
            ItemStack exactPostStack
    ) {
        if (endpointProtocolVersion != WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION
                || slotIndex < 0 || slotIndex >= inventory.totalSlotCount()
                || !exactStack(inventory.getStackInSlot(slotIndex), exactPreStack)) {
            return false;
        }
        if (!exactPostStack.isEmpty()) {
            if (exactPostStack.getCount() > inventory.effectiveSlotCapacity(slotIndex, exactPostStack)) return false;
            if (kind == WorkstationEndpointEffectKind.DESTINATION_DEPOSIT
                    && (!inventory.isInputSlot(slotIndex) || !inventory.isItemValid(slotIndex, exactPostStack))) {
                return false;
            }
            if (kind == WorkstationEndpointEffectKind.SOURCE_RETURN
                    && inventory.isInputSlot(slotIndex) && !inventory.isItemValid(slotIndex, exactPostStack)) {
                return false;
            }
        }
        return switch (kind) {
            case SOURCE_WITHDRAWAL -> inventory.isInputSlot(slotIndex) || inventory.isOutputSlot(slotIndex);
            case DESTINATION_DEPOSIT -> inventory.isInputSlot(slotIndex);
            case SOURCE_RETURN -> inventory.isInputSlot(slotIndex) || inventory.isOutputSlot(slotIndex);
        };
    }

    protected final void lockStackAwareEndpointEffectView(
            WorkstationEndpointEffectIdV2 effectId,
            int slotIndex,
            long expectedInventoryRevision
    ) {
        Objects.requireNonNull(effectId, "effectId");
        if (endpointProtocolVersion != WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION) {
            throw new IllegalStateException("Schema-2 endpoint projection has not been activated");
        }
        if (stackAwarePreparedEffectId.isPresent()
                && stackAwarePreparedEffectId.filter(effectId::equals).isEmpty()) {
            throw new IllegalStateException("Workstation endpoint slot is locked by another schema-2 effect");
        }
        if (endpointProjection.inventoryRevision() != expectedInventoryRevision) {
            throw new IllegalStateException("Workstation inventory freshness changed before schema-2 endpoint lock");
        }
        stackAwarePreparedEffectId = Optional.of(effectId);
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(), endpointProjection.instanceGeneration(),
                endpointProjection.inventoryRevision(), endpointProjection.endpointEffectRevision(),
                endpointProjection.lastAppliedJournalSequence(), endpointProjection.lastEffectId(),
                endpointProjection.lastOwnerResultIdentity(), Optional.empty(), slotIndex, expectedInventoryRevision
        );
        setChanged();
    }

    protected final void releaseStackAwareEndpointEffectView(WorkstationEndpointEffectIdV2 effectId) {
        Objects.requireNonNull(effectId, "effectId");
        if (stackAwarePreparedEffectId.filter(effectId::equals).isEmpty()) return;
        stackAwarePreparedEffectId = Optional.empty();
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(), endpointProjection.instanceGeneration(),
                endpointProjection.inventoryRevision(), endpointProjection.endpointEffectRevision(),
                endpointProjection.lastAppliedJournalSequence(), endpointProjection.lastEffectId(),
                endpointProjection.lastOwnerResultIdentity(), Optional.empty(), -1, 0L
        );
        setChanged();
    }

    protected final void applyCommittedStackAwareEndpointEffectView(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactPreStack,
            ItemStack exactPostStack,
            long expectedInventoryRevision,
            long postInventoryRevision,
            long endpointEffectRevision,
            long journalSequence,
            WorkstationEndpointEffectIdV2 effectId,
            String ownerResultIdentity
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(ownerResultIdentity, "ownerResultIdentity");
        if (endpointProjection.inventoryRevision() != expectedInventoryRevision
                || endpointProjection.preparedInventoryRevision() != expectedInventoryRevision
                || stackAwarePreparedEffectId.filter(effectId::equals).isEmpty()
                || !exactStack(inventory.getStackInSlot(slotIndex), exactPreStack)) {
            throw new IllegalStateException("Schema-2 endpoint projection freshness or lock changed");
        }
        if (postInventoryRevision != Math.addExact(expectedInventoryRevision, 1L)
                || endpointEffectRevision != Math.addExact(endpointProjection.endpointEffectRevision(), 1L)
                || journalSequence <= endpointProjection.lastAppliedJournalSequence()) {
            throw new IllegalArgumentException("Schema-2 endpoint revisions are not monotonic");
        }
        if (inventory.isInputSlot(slotIndex)) {
            inventory.setInputInternal(slotIndex - inventory.firstInputSlot(), exactPostStack.copy());
        } else {
            inventory.setOutputInternal(slotIndex - inventory.firstOutputSlot(), exactPostStack.copy());
        }
        if (endpointProjection.inventoryRevision() != postInventoryRevision) {
            throw new IllegalStateException("Schema-2 endpoint projection did not publish the expected revision");
        }
        stackAwarePreparedEffectId = Optional.empty();
        stackAwareLastEffectId = Optional.of(effectId);
        stackAwareLastOwnerResultIdentity = Optional.of(ownerResultIdentity);
        endpointProjection = new WorkstationEndpointProjection(
                endpointProjection.instanceId(), endpointProjection.instanceGeneration(),
                endpointProjection.inventoryRevision(), endpointEffectRevision, journalSequence,
                Optional.empty(), Optional.empty(), Optional.empty(), -1, 0L
        );
        setChanged();
    }

    private static boolean exactStack(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();
        return left.getCount() == right.getCount() && ItemStack.isSameItemSameComponents(left, right);
    }
}
