package com.butchercraft.machine.cuttingtable;

import com.butchercraft.machine.cuttingtable.execution.CuttingTableExecutionCoordinator;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationExecutionEffectResult;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectIdV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointObservationV2;
import com.butchercraft.workstation.endpoint.WorkstationEndpointConfiguration;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.runtime.StackAwareWorkstationTransferEndpoint;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointProjection;
import com.butchercraft.workstation.endpoint.runtime.WorkstationTransferEndpoint;
import com.butchercraft.world.execution.ExecutionDomainEffectIdentity;
import com.butchercraft.world.execution.ExecutionOperationId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CuttingTableBlockEntity extends AbstractProcessingWorkstationBlockEntity
        implements WorkstationTransferEndpoint, StackAwareWorkstationTransferEndpoint {
    public CuttingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(
                ModBlockEntityTypes.CUTTING_TABLE.get(),
                pos,
                blockState,
                CuttingTableWorkstation.capability(),
                new WorkstationOperationResolver(),
                DevelopmentProductItemMappings.fixtureMapping(),
                WorkstationExecutionStrategy.atomicTransformation(),
                CuttingTableExecutionCoordinator.INSTANCE,
                com.butchercraft.workstation.WorkstationOperationStartPolicy.AUTOMATIC_WHEN_READY,
                CuttingTableWorkstation.slotCapacityPolicy()
        );
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CuttingTableBlockEntity blockEntity) {
        AbstractProcessingWorkstationBlockEntity.serverTick(level, pos, state, blockEntity);
    }

    public WorkstationExecutionEffectResult completeScheduledExecution(
            ExecutionOperationId operationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            long authoritativeTick
    ) {
        return super.completeScheduledExecution(operationId, domainEffectIdentity, authoritativeTick);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.butchercraft.cutting_table");
    }

    @Nullable
    @Override
    protected AbstractContainerMenu createWorkstationMenu(int containerId, Inventory playerInventory, Player player) {
        return new CuttingTableMenu(containerId, playerInventory, this);
    }

    @Override
    public String endpointTypeIdentity() {
        return CuttingTableWorkstation.ID.toString();
    }

    @Override
    public String endpointOperationStateIdentity() {
        return "butchercraft:cutting_table/" + workstationState().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String endpointPostOperationStateIdentity(WorkstationEndpointEffectKind kind) {
        return endpointOperationStateIdentity();
    }

    @Override
    public WorkstationEndpointProjection endpointProjection() {
        return endpointProjectionView();
    }

    @Override
    public int endpointSlotIndex(WorkstationEndpointEffectKind kind) {
        return kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                || kind == WorkstationEndpointEffectKind.SOURCE_RETURN
                ? trimOutputSlot()
                : -1;
    }

    @Override
    public ItemStack endpointStackSnapshot(int slotIndex) {
        return endpointStackSnapshotView(slotIndex);
    }

    @Override
    public boolean endpointAccepts(WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactStack) {
        return (kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                || kind == WorkstationEndpointEffectKind.SOURCE_RETURN)
                && slotIndex == trimOutputSlot()
                && exactStack.is(ModItems.BEEF_TRIM.get())
                && exactStack.getCount() == 1
                && endpointAcceptsView(kind, slotIndex, exactStack);
    }

    @Override public void activateStackAwareEndpoint() { activateStackAwareEndpointView(); }
    @Override public WorkstationInstanceId endpointInstanceId() { return stackAwareEndpointInstanceIdView(); }
    @Override public WorkstationEndpointKey endpointKey() { return stackAwareEndpointKeyView(endpointTypeIdentity()); }
    @Override public String endpointPostOperationStateIdentity(WorkstationEndpointObservationV2 observation) {
        return endpointOperationStateIdentity();
    }
    @Override public String endpointConfigurationIdentity() {
        return WorkstationEndpointConfiguration.standard().stackAwareEndpointConfigurationIdentity();
    }
    @Override public int endpointEffectiveCapacity(int slotIndex, ItemStack stack) {
        return stackAwareEffectiveCapacityView(slotIndex, stack);
    }
    @Override public long endpointInventoryRevision() { return stackAwareInventoryRevisionView(); }
    @Override public long endpointEffectRevision() { return stackAwareEffectRevisionView(); }
    @Override public long endpointLastAppliedJournalSequence() { return stackAwareLastJournalSequenceView(); }
    @Override public java.util.Optional<WorkstationEndpointEffectIdV2> endpointPreparedEffectId() {
        return stackAwarePreparedEffectIdView();
    }
    @Override public java.util.Optional<WorkstationEndpointEffectIdV2> endpointLastEffectId() {
        return stackAwareLastEffectIdView();
    }
    @Override public java.util.Optional<String> endpointLastOwnerResultIdentity() {
        return stackAwareLastOwnerResultIdentityView();
    }
    @Override public boolean endpointAcceptsCandidate(
            WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactPreStack, ItemStack exactPostStack) {
        if ((kind != WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                && kind != WorkstationEndpointEffectKind.SOURCE_RETURN)
                || slotIndex != trimOutputSlot()
                || !stackAwareAcceptsCandidateView(kind, slotIndex, exactPreStack, exactPostStack)) {
            return false;
        }
        return kind == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                ? exactPreStack.is(ModItems.BEEF_TRIM.get())
                && exactPreStack.getCount() - exactPostStack.getCount() == 1
                : exactPostStack.is(ModItems.BEEF_TRIM.get())
                && exactPostStack.getCount() - exactPreStack.getCount() == 1;
    }
    @Override public void lockPreparedEndpointEffect(
            WorkstationEndpointEffectIdV2 effectId, int slotIndex, long expectedInventoryRevision) {
        lockStackAwareEndpointEffectView(effectId, slotIndex, expectedInventoryRevision);
    }
    @Override public void releasePreparedEndpointEffect(WorkstationEndpointEffectIdV2 effectId) {
        releaseStackAwareEndpointEffectView(effectId);
    }
    @Override public void applyCommittedEndpointEffect(
            WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactPreStack, ItemStack exactPostStack,
            long expectedInventoryRevision, long postInventoryRevision, long endpointEffectRevision,
            long journalSequence, WorkstationEndpointEffectIdV2 effectId, String ownerResultIdentity) {
        if (!endpointAcceptsCandidate(kind, slotIndex, exactPreStack, exactPostStack)) {
            throw new IllegalStateException("Cutting Table rejected the schema-2 transfer endpoint effect");
        }
        applyCommittedStackAwareEndpointEffectView(
                kind, slotIndex, exactPreStack, exactPostStack, expectedInventoryRevision, postInventoryRevision,
                endpointEffectRevision, journalSequence, effectId, ownerResultIdentity
        );
    }

    public DevelopmentOutputPreloadStatus preloadOutputForDevelopment(ItemStack exactStack) {
        Objects.requireNonNull(exactStack, "exactStack");
        if (level == null || level.isClientSide
                || !exactStack.is(ModItems.BEEF_TRIM.get())
                || exactStack.getCount() <= 0
                || exactStack.getCount() > inventory().effectiveSlotCapacity(trimOutputSlot(), exactStack)) {
            return DevelopmentOutputPreloadStatus.INVALID_STACK;
        }
        if (inventory().isTransferLocked(trimOutputSlot())) {
            return DevelopmentOutputPreloadStatus.ENDPOINT_LOCKED;
        }
        ItemStack current = inventory().getStackInSlot(trimOutputSlot());
        if (current.isEmpty()) {
            inventory().setOutputInternal(1, exactStack.copy());
            return DevelopmentOutputPreloadStatus.PRELOADED;
        }
        if (ItemStack.isSameItemSameComponents(current, exactStack)) {
            if (current.getCount() == exactStack.getCount()) return DevelopmentOutputPreloadStatus.ALREADY_PRESENT;
            try {
                var merged = com.butchercraft.workstation.WorkstationStackMutationPlan.merge(
                        current, exactStack, inventory().slotCapacityPolicy().capacity(trimOutputSlot()));
                inventory().setOutputInternal(1, merged.postStack());
                return DevelopmentOutputPreloadStatus.PRELOADED;
            } catch (IllegalArgumentException ignored) {
                return DevelopmentOutputPreloadStatus.OUTPUT_OCCUPIED;
            }
        }
        return DevelopmentOutputPreloadStatus.OUTPUT_OCCUPIED;
    }

    public int primaryOutputSlot() {
        return inventory().firstOutputSlot();
    }

    public int trimOutputSlot() {
        return inventory().firstOutputSlot() + 1;
    }

    @Override
    public void bindEndpointInstance(WorkstationInstanceId instanceId, long generation) {
        bindEndpointInstanceView(instanceId, generation);
    }

    @Override
    public void lockPreparedEndpointEffect(
            WorkstationEndpointEffectId effectId,
            int slotIndex,
            long expectedInventoryRevision
    ) {
        lockPreparedEndpointEffectView(effectId, slotIndex, expectedInventoryRevision);
    }

    @Override
    public void releasePreparedEndpointEffect(WorkstationEndpointEffectId effectId) {
        releasePreparedEndpointEffectView(effectId);
    }

    @Override
    public void applyCommittedEndpointEffect(
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
        if (!endpointAccepts(kind, slotIndex, exactStack)) {
            throw new IllegalStateException("Cutting Table rejected the committed transfer endpoint effect");
        }
        applyCommittedEndpointEffectView(
                kind,
                slotIndex,
                exactStack,
                expectedInventoryRevision,
                postInventoryRevision,
                endpointEffectRevision,
                journalSequence,
                effectId,
                ownerResultIdentity
        );
    }

    public enum DevelopmentOutputPreloadStatus {
        PRELOADED,
        ALREADY_PRESENT,
        OUTPUT_OCCUPIED,
        ENDPOINT_LOCKED,
        INVALID_STACK
    }
}
