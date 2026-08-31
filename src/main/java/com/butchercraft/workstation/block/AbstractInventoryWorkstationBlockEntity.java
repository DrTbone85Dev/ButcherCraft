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
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.List;

/**
 * Shared block-entity foundation for workstation blocks that own a bounded inventory and menu.
 */
public abstract class AbstractInventoryWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private static final String INVENTORY_TAG = "Inventory";
    private static final String ENDPOINT_PROJECTION_TAG = "TransferEndpointProjection";
    private static final String DURABLE_PROJECTION_REFERENCE_TAG = "DurableProjectionReference";

    private final WorkstationInventory inventory;
    private final WorkstationCapability capability;
    private final ContainerData menuData = new SimpleContainerData(4);
    private WorkstationEndpointProjection endpointProjection = WorkstationEndpointProjection.unbound(0L);
    private int endpointProtocolVersion = WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION;
    private Optional<WorkstationEndpointEffectIdV2> stackAwarePreparedEffectId = Optional.empty();
    private Optional<WorkstationEndpointEffectIdV2> stackAwareLastEffectId = Optional.empty();
    private Optional<String> stackAwareLastOwnerResultIdentity = Optional.empty();
    private Optional<WorkstationEndpointEffectId> recoveredLegacyLastEffectId = Optional.empty();
    private Optional<String> recoveredLegacyLastOwnerResultIdentity = Optional.empty();
    private long durableProjectionRevision;
    private Optional<String> durableProjectionDigest = Optional.empty();
    private boolean durableProjectionReady;
    private boolean durableProjectionPublicationSuppressed;
    private int durableProjectionMutationDepth;
    private boolean durableProjectionMutationChanged;

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
        if (stackAwareLastEffectId.isPresent()) {
            endpointTag.putString("StackAwareLastEffectIdentity", stackAwareLastEffectId.orElseThrow().value());
            endpointTag.putString("StackAwareLastOwnerResultIdentity",
                    stackAwareLastOwnerResultIdentity.orElseThrow());
        } else if (recoveredLegacyLastEffectId.isPresent()) {
            endpointTag.putString("StackAwareLastEffectIdentity", recoveredLegacyLastEffectId.orElseThrow().value());
            endpointTag.putString("StackAwareLastOwnerResultIdentity",
                    recoveredLegacyLastOwnerResultIdentity.orElseThrow());
        }
        endpointTag.putInt("PreparedSlotIndex", endpointProjection.preparedSlotIndex());
        endpointTag.putLong("PreparedInventoryRevision", endpointProjection.preparedInventoryRevision());
        tag.put(ENDPOINT_PROJECTION_TAG, endpointTag);
        if (durableProjectionRevision > 0L) {
            CompoundTag durableReference = new CompoundTag();
            durableReference.putInt("SchemaVersion", 1);
            durableReference.putLong("ProjectionRevision", durableProjectionRevision);
            durableReference.putString("StateDigest", durableProjectionDigest.orElseThrow());
            tag.put(DURABLE_PROJECTION_REFERENCE_TAG, durableReference);
        }
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
            StackAwareEffectMarkers stackAwareMarkers = readStackAwareEffectMarkers(endpointTag);
            if (schemaVersion == WorkstationEndpointSchema.LEGACY_ENDPOINT_PROTOCOL_VERSION
                    && (stackAwarePrepared.isPresent() || stackAwareMarkers.hasAnyMarker())) {
                throw new IllegalStateException("Schema-1 endpoint projection contains schema-2 effect identity");
            }
            if (schemaVersion == WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION
                    && (preparedEffect.isPresent() || lastEffect.isPresent() || lastResult.isPresent())) {
                throw new IllegalStateException("Schema-2 endpoint projection contains schema-1 effect markers");
            }
            if (stackAwareMarkers.hasAnyMarker()
                    && endpointTag.getLong("LastAppliedJournalSequence") <= 0L) {
                throw new IllegalStateException("Schema-2 endpoint result marker requires a journal sequence");
            }
            endpointProtocolVersion = schemaVersion;
            stackAwarePreparedEffectId = stackAwarePrepared;
            stackAwareLastEffectId = stackAwareMarkers.activeEffectId();
            stackAwareLastOwnerResultIdentity = stackAwareMarkers.activeOwnerResultIdentity();
            recoveredLegacyLastEffectId = stackAwareMarkers.recoveredLegacyEffectId();
            recoveredLegacyLastOwnerResultIdentity = stackAwareMarkers.recoveredLegacyOwnerResultIdentity();
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
        durableProjectionRevision = 0L;
        durableProjectionDigest = Optional.empty();
        durableProjectionReady = false;
        if (tag.contains(DURABLE_PROJECTION_REFERENCE_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag durableReference = tag.getCompound(DURABLE_PROJECTION_REFERENCE_TAG);
            int schema = durableReference.getInt("SchemaVersion");
            if (schema != 1) {
                throw new IllegalStateException("Unsupported durable Workstation projection reference schema: " + schema);
            }
            long revision = durableReference.getLong("ProjectionRevision");
            String digest = durableReference.getString("StateDigest");
            if (revision <= 0L || digest.isBlank()) {
                throw new IllegalStateException("Invalid durable Workstation projection reference");
            }
            durableProjectionRevision = revision;
            durableProjectionDigest = Optional.of(digest);
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
        if (level instanceof ServerLevel serverLevel && this instanceof WorkstationTransferEndpoint) {
            var reconciliation = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(serverLevel, this);
            if (!reconciliation.succeeded()) {
                throw new IllegalStateException("Durable Workstation projection reconciliation failed: "
                        + reconciliation.code() + ": " + reconciliation.detail());
            }
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (durableProjectionMutationDepth > 0) {
            durableProjectionMutationChanged = true;
            return;
        }
        if (!durableProjectionPublicationSuppressed && durableProjectionReady
                && level instanceof ServerLevel serverLevel) {
            DurableWorkstationProjectionService.INSTANCE.publishAuthorizedMutation(serverLevel, this);
        }
    }

    protected final void beginDurableProjectionMutation() {
        durableProjectionMutationDepth = Math.addExact(durableProjectionMutationDepth, 1);
    }

    protected final void endDurableProjectionMutation() {
        if (durableProjectionMutationDepth <= 0) {
            throw new IllegalStateException("Durable Workstation projection mutation boundary is unbalanced");
        }
        durableProjectionMutationDepth--;
        if (durableProjectionMutationDepth == 0 && durableProjectionMutationChanged) {
            durableProjectionMutationChanged = false;
            setChanged();
        }
    }

    protected final WorkstationEndpointProjection endpointProjectionView() {
        return endpointProjection;
    }

    public final Optional<WorkstationInstanceId> checkpointInstanceIdentity() {
        return endpointProjection.instanceId();
    }

    public final long checkpointInstanceGeneration() {
        return endpointProjection.instanceGeneration();
    }

    public final CompoundTag checkpointProjectionSnapshot(HolderLookup.Provider registries) {
        return saveWithoutMetadata(Objects.requireNonNull(registries, "registries")).copy();
    }

    public final CompoundTag durableProjectionStateSnapshot(HolderLookup.Provider registries) {
        CompoundTag projection = saveWithoutMetadata(Objects.requireNonNull(registries, "registries")).copy();
        projection.remove(DURABLE_PROJECTION_REFERENCE_TAG);
        return projection;
    }

    public final void restoreDurableProjectionState(
            CompoundTag projection,
            HolderLookup.Provider registries
    ) {
        durableProjectionPublicationSuppressed = true;
        try {
            loadAdditional(
                    Objects.requireNonNull(projection, "projection").copy(),
                    Objects.requireNonNull(registries, "registries")
            );
            super.setChanged();
        } finally {
            durableProjectionPublicationSuppressed = false;
        }
    }

    public final void acceptDurableProjectionReference(long revision, String stateDigest) {
        if (revision <= 0L) throw new IllegalArgumentException("Durable projection revision must be positive");
        Objects.requireNonNull(stateDigest, "stateDigest");
        if (revision < durableProjectionRevision) {
            throw new IllegalStateException("Durable Workstation projection revision cannot regress");
        }
        durableProjectionPublicationSuppressed = true;
        try {
            durableProjectionRevision = revision;
            durableProjectionDigest = Optional.of(stateDigest);
            durableProjectionReady = true;
            super.setChanged();
        } finally {
            durableProjectionPublicationSuppressed = false;
        }
    }

    public final void markDurableProjectionReady() {
        durableProjectionReady = true;
    }

    protected final void ensureDurableProjectionReady() {
        if (durableProjectionReady || !(level instanceof ServerLevel serverLevel)
                || !(this instanceof WorkstationTransferEndpoint)) {
            return;
        }
        var reconciliation = DurableWorkstationProjectionService.INSTANCE.reconcileLoaded(serverLevel, this);
        if (!reconciliation.succeeded()) {
            throw new IllegalStateException("Durable Workstation projection initialization failed: "
                    + reconciliation.code() + ": " + reconciliation.detail());
        }
    }

    public final long durableProjectionRevision() {
        return durableProjectionRevision;
    }

    public final Optional<String> durableProjectionDigest() {
        return durableProjectionDigest;
    }

    public final String durableBlockEntityTypeIdentity() {
        return Objects.requireNonNull(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType()), "blockEntityType").toString();
    }

    public final List<ItemStack> durableInventorySnapshot() {
        java.util.ArrayList<ItemStack> slots = new java.util.ArrayList<>(inventory.totalSlotCount());
        for (int slot = 0; slot < inventory.totalSlotCount(); slot++) {
            slots.add(inventory.getStackInSlot(slot).copy());
        }
        return List.copyOf(slots);
    }

    public final int durableConfiguredSlotCapacity(int slot) {
        return inventory.slotCapacityPolicy().capacity(slot);
    }

    public final int durableEffectiveSlotCapacity(int slot, ItemStack stack) {
        return stack.isEmpty()
                ? inventory.slotCapacityPolicy().capacity(slot)
                : inventory.effectiveSlotCapacity(slot, stack);
    }

    public final String durableSlotCapacityConfigurationIdentity() {
        return inventory.slotCapacityPolicy().configurationIdentity();
    }

    public final long durableInventoryRevision() {
        return endpointProjection.inventoryRevision();
    }

    public final long durableEndpointEffectRevision() {
        return endpointProjection.endpointEffectRevision();
    }

    public final long durableLastAppliedJournalSequence() {
        return endpointProjection.lastAppliedJournalSequence();
    }

    public final Optional<String> durablePreparedEndpointEffectIdentity() {
        if (stackAwarePreparedEffectId.isPresent()) {
            return stackAwarePreparedEffectId.map(WorkstationEndpointEffectIdV2::value);
        }
        return endpointProjection.preparedEffectId().map(WorkstationEndpointEffectId::value);
    }

    public final Optional<String> durableLastEndpointEffectIdentity() {
        if (stackAwareLastEffectId.isPresent()) {
            return stackAwareLastEffectId.map(WorkstationEndpointEffectIdV2::value);
        }
        if (recoveredLegacyLastEffectId.isPresent()) {
            return recoveredLegacyLastEffectId.map(WorkstationEndpointEffectId::value);
        }
        return endpointProjection.lastEffectId().map(WorkstationEndpointEffectId::value);
    }

    public final Optional<String> durableLastEndpointOwnerResultIdentity() {
        if (stackAwareLastOwnerResultIdentity.isPresent()) return stackAwareLastOwnerResultIdentity;
        if (recoveredLegacyLastOwnerResultIdentity.isPresent()) return recoveredLegacyLastOwnerResultIdentity;
        return endpointProjection.lastOwnerResultIdentity();
    }

    public final Optional<String> durableProcessingOperationIdentity() {
        return durableProcessingOperationIdentityView();
    }

    public final Optional<String> durableProcessingOwnerResultIdentity() {
        return durableProcessingOwnerResultIdentityView();
    }

    protected Optional<String> durableProcessingOperationIdentityView() {
        return Optional.empty();
    }

    protected Optional<String> durableProcessingOwnerResultIdentityView() {
        return Optional.empty();
    }

    public final void restoreCheckpointProjection(
            CompoundTag projection,
            HolderLookup.Provider registries
    ) {
        loadAdditional(
                Objects.requireNonNull(projection, "projection").copy(),
                Objects.requireNonNull(registries, "registries")
        );
        setChanged();
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
        beginDurableProjectionMutation();
        try {
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
        } finally {
            endDurableProjectionMutation();
        }
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
        recoveredLegacyLastEffectId = Optional.empty();
        recoveredLegacyLastOwnerResultIdentity = Optional.empty();
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
        beginDurableProjectionMutation();
        try {
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
            recoveredLegacyLastEffectId = Optional.empty();
            recoveredLegacyLastOwnerResultIdentity = Optional.empty();
            endpointProjection = new WorkstationEndpointProjection(
                    endpointProjection.instanceId(), endpointProjection.instanceGeneration(),
                    endpointProjection.inventoryRevision(), endpointEffectRevision, journalSequence,
                    Optional.empty(), Optional.empty(), Optional.empty(), -1, 0L
            );
            setChanged();
        } finally {
            endDurableProjectionMutation();
        }
    }

    static StackAwareEffectMarkers readStackAwareEffectMarkers(CompoundTag endpointTag) {
        boolean hasEffect = endpointTag.contains("StackAwareLastEffectIdentity", Tag.TAG_STRING);
        boolean hasResult = endpointTag.contains("StackAwareLastOwnerResultIdentity", Tag.TAG_STRING);
        if (hasEffect != hasResult) {
            throw new IllegalStateException("Schema-2 endpoint result markers must be published together");
        }
        if (!hasEffect) return StackAwareEffectMarkers.empty();

        String effectIdentity = endpointTag.getString("StackAwareLastEffectIdentity");
        String ownerResultIdentity = endpointTag.getString("StackAwareLastOwnerResultIdentity");
        if (WorkstationEndpointEffectIdV2.hasCanonicalPrefix(effectIdentity)) {
            return StackAwareEffectMarkers.active(
                    new WorkstationEndpointEffectIdV2(effectIdentity), ownerResultIdentity);
        }
        return StackAwareEffectMarkers.recoveredLegacy(
                new WorkstationEndpointEffectId(effectIdentity), ownerResultIdentity);
    }

    record StackAwareEffectMarkers(
            Optional<WorkstationEndpointEffectIdV2> activeEffectId,
            Optional<String> activeOwnerResultIdentity,
            Optional<WorkstationEndpointEffectId> recoveredLegacyEffectId,
            Optional<String> recoveredLegacyOwnerResultIdentity
    ) {
        StackAwareEffectMarkers {
            activeEffectId = Objects.requireNonNull(activeEffectId, "activeEffectId");
            activeOwnerResultIdentity = Objects.requireNonNull(
                    activeOwnerResultIdentity, "activeOwnerResultIdentity");
            recoveredLegacyEffectId = Objects.requireNonNull(
                    recoveredLegacyEffectId, "recoveredLegacyEffectId");
            recoveredLegacyOwnerResultIdentity = Objects.requireNonNull(
                    recoveredLegacyOwnerResultIdentity, "recoveredLegacyOwnerResultIdentity");
            if (activeEffectId.isEmpty() != activeOwnerResultIdentity.isEmpty()
                    || recoveredLegacyEffectId.isEmpty() != recoveredLegacyOwnerResultIdentity.isEmpty()
                    || (activeEffectId.isPresent() && recoveredLegacyEffectId.isPresent())) {
                throw new IllegalArgumentException("Stack-aware endpoint effect markers are inconsistent");
            }
        }

        static StackAwareEffectMarkers empty() {
            return new StackAwareEffectMarkers(
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static StackAwareEffectMarkers active(WorkstationEndpointEffectIdV2 effectId, String ownerResultIdentity) {
            return new StackAwareEffectMarkers(
                    Optional.of(effectId), Optional.of(ownerResultIdentity), Optional.empty(), Optional.empty());
        }

        static StackAwareEffectMarkers recoveredLegacy(
                WorkstationEndpointEffectId effectId,
                String ownerResultIdentity
        ) {
            return new StackAwareEffectMarkers(
                    Optional.empty(), Optional.empty(), Optional.of(effectId), Optional.of(ownerResultIdentity));
        }

        boolean hasAnyMarker() {
            return activeEffectId.isPresent() || recoveredLegacyEffectId.isPresent();
        }
    }

    private static boolean exactStack(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();
        return left.getCount() == right.getCount() && ItemStack.isSameItemSameComponents(left, right);
    }
}
