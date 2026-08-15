package com.butchercraft.machine.grinder;

import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModItems;
import com.butchercraft.machine.grinder.execution.GrinderExecutionCoordinator;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationExecutionEffectResult;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
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
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.execution.ExecutionDomainEffectIdentity;
import com.butchercraft.world.execution.ExecutionOperationId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class GrinderBlockEntity extends AbstractProcessingWorkstationBlockEntity
        implements WorkstationTransferEndpoint, StackAwareWorkstationTransferEndpoint {
    public GrinderBlockEntity(BlockPos pos, BlockState blockState) {
        super(
                ModBlockEntityTypes.GRINDER.get(),
                pos,
                blockState,
                GrinderWorkstation.capability(),
                new WorkstationOperationResolver(),
                DevelopmentProductItemMappings.fixtureMapping(),
                WorkstationExecutionStrategy.transformation(),
                GrinderExecutionCoordinator.INSTANCE,
                com.butchercraft.workstation.WorkstationOperationStartPolicy.EXPLICIT_REQUEST,
                GrinderWorkstation.slotCapacityPolicy()
        );
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrinderBlockEntity blockEntity) {
        if (level instanceof ServerLevel serverLevel
                && (blockEntity.workstationState() == WorkstationState.IDLE
                || blockEntity.workstationState() == WorkstationState.READY)
                && WorkstationReservationService.INSTANCE.hasActiveReservationAt(serverLevel, pos)) {
            return;
        }
        AbstractProcessingWorkstationBlockEntity.serverTick(level, pos, state, blockEntity);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.butchercraft.grinder");
    }

    public WorkstationExecutionEffectResult completeScheduledExecution(
            ExecutionOperationId operationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            long authoritativeTick
    ) {
        return super.completeScheduledExecution(operationId, domainEffectIdentity, authoritativeTick);
    }

    public WorkstationProductionRequestResult requestEmployeeProcessing(WorkstationTickContext tickContext) {
        return requestProductionProcessing(tickContext);
    }

    public WorkstationProductionRequestResult requestPlayerProcessing(WorkstationTickContext tickContext) {
        return requestProductionProcessing(tickContext);
    }

    @Override
    public String endpointTypeIdentity() {
        return GrinderWorkstation.ID.toString();
    }

    @Override
    public String endpointOperationStateIdentity() {
        return "butchercraft:grinder/" + workstationState().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String endpointPostOperationStateIdentity(WorkstationEndpointEffectKind kind) {
        return switch (kind) {
            case DESTINATION_DEPOSIT -> "butchercraft:grinder/ready";
            case SOURCE_WITHDRAWAL, SOURCE_RETURN -> "butchercraft:grinder/idle";
        };
    }

    @Override
    public WorkstationEndpointProjection endpointProjection() {
        return endpointProjectionView();
    }

    @Override
    public int endpointSlotIndex(WorkstationEndpointEffectKind kind) {
        return switch (kind) {
            case DESTINATION_DEPOSIT -> inventory().firstInputSlot();
            case SOURCE_WITHDRAWAL, SOURCE_RETURN -> inventory().firstOutputSlot();
        };
    }

    @Override
    public ItemStack endpointStackSnapshot(int slotIndex) {
        return endpointStackSnapshotView(slotIndex);
    }

    @Override
    public boolean endpointAccepts(WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactStack) {
        if (exactStack.getCount() != 1) {
            return false;
        }
        return switch (kind) {
            case DESTINATION_DEPOSIT -> workstationState() == WorkstationState.IDLE
                    && exactStack.is(ModItems.BEEF_TRIM.get())
                    && endpointAcceptsView(kind, slotIndex, exactStack);
            case SOURCE_WITHDRAWAL -> (workstationState() == WorkstationState.COMPLETE
                    || workstationState() == WorkstationState.IDLE)
                    && exactStack.is(ModItems.GROUND_BEEF.get())
                    && endpointAcceptsView(kind, slotIndex, exactStack);
            case SOURCE_RETURN -> workstationState() == WorkstationState.IDLE
                    && exactStack.is(ModItems.GROUND_BEEF.get())
                    && endpointAcceptsView(kind, slotIndex, exactStack);
        };
    }

    @Override
    public void activateStackAwareEndpoint() {
        activateStackAwareEndpointView();
    }

    @Override
    public WorkstationInstanceId endpointInstanceId() {
        return stackAwareEndpointInstanceIdView();
    }

    @Override
    public WorkstationEndpointKey endpointKey() {
        return stackAwareEndpointKeyView(endpointTypeIdentity());
    }

    @Override
    public String endpointPostOperationStateIdentity(WorkstationEndpointObservationV2 observation) {
        if (observation.effectKind() == WorkstationEndpointEffectKind.DESTINATION_DEPOSIT) {
            return "butchercraft:grinder/ready";
        }
        if (observation.effectKind() == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL
                && workstationState() == WorkstationState.COMPLETE
                && observation.postStack().isEmpty()) {
            return "butchercraft:grinder/idle";
        }
        return endpointOperationStateIdentity();
    }

    @Override
    public String endpointConfigurationIdentity() {
        return WorkstationEndpointConfiguration.standard().stackAwareEndpointConfigurationIdentity();
    }

    @Override
    public int endpointEffectiveCapacity(int slotIndex, ItemStack stack) {
        return stackAwareEffectiveCapacityView(slotIndex, stack);
    }

    @Override
    public long endpointInventoryRevision() {
        return stackAwareInventoryRevisionView();
    }

    @Override
    public long endpointEffectRevision() {
        return stackAwareEffectRevisionView();
    }

    @Override
    public long endpointLastAppliedJournalSequence() {
        return stackAwareLastJournalSequenceView();
    }

    @Override
    public java.util.Optional<WorkstationEndpointEffectIdV2> endpointPreparedEffectId() {
        return stackAwarePreparedEffectIdView();
    }

    @Override
    public java.util.Optional<WorkstationEndpointEffectIdV2> endpointLastEffectId() {
        return stackAwareLastEffectIdView();
    }

    @Override
    public java.util.Optional<String> endpointLastOwnerResultIdentity() {
        return stackAwareLastOwnerResultIdentityView();
    }

    @Override
    public boolean endpointAcceptsCandidate(
            WorkstationEndpointEffectKind kind,
            int slotIndex,
            ItemStack exactPreStack,
            ItemStack exactPostStack
    ) {
        if (!stackAwareAcceptsCandidateView(kind, slotIndex, exactPreStack, exactPostStack)) return false;
        return switch (kind) {
            case DESTINATION_DEPOSIT -> (workstationState() == WorkstationState.IDLE
                    || workstationState() == WorkstationState.READY)
                    && slotIndex == inventory().firstInputSlot()
                    && exactPostStack.is(ModItems.BEEF_TRIM.get())
                    && exactPostStack.getCount() - exactPreStack.getCount() == 1;
            case SOURCE_WITHDRAWAL -> (workstationState() == WorkstationState.COMPLETE
                    || workstationState() == WorkstationState.IDLE)
                    && slotIndex == inventory().firstOutputSlot()
                    && exactPreStack.is(ModItems.GROUND_BEEF.get())
                    && exactPreStack.getCount() - exactPostStack.getCount() == 1;
            case SOURCE_RETURN -> (workstationState() == WorkstationState.IDLE
                    || workstationState() == WorkstationState.COMPLETE)
                    && slotIndex == inventory().firstOutputSlot()
                    && exactPostStack.is(ModItems.GROUND_BEEF.get())
                    && exactPostStack.getCount() - exactPreStack.getCount() == 1;
        };
    }

    @Override
    public void lockPreparedEndpointEffect(
            WorkstationEndpointEffectIdV2 effectId,
            int slotIndex,
            long expectedInventoryRevision
    ) {
        lockStackAwareEndpointEffectView(effectId, slotIndex, expectedInventoryRevision);
    }

    @Override
    public void releasePreparedEndpointEffect(WorkstationEndpointEffectIdV2 effectId) {
        releaseStackAwareEndpointEffectView(effectId);
    }

    @Override
    public void applyCommittedEndpointEffect(
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
        if (!endpointAcceptsCandidate(kind, slotIndex, exactPreStack, exactPostStack)) {
            throw new IllegalStateException("Grinder rejected the schema-2 transfer endpoint effect");
        }
        applyCommittedStackAwareEndpointEffectView(
                kind, slotIndex, exactPreStack, exactPostStack, expectedInventoryRevision, postInventoryRevision,
                endpointEffectRevision, journalSequence, effectId, ownerResultIdentity
        );
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
            throw new IllegalStateException("Grinder rejected the committed transfer endpoint effect");
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

    @Nullable
    @Override
    protected AbstractContainerMenu createWorkstationMenu(int containerId, Inventory playerInventory, Player player) {
        return new GrinderMenu(containerId, playerInventory, this);
    }
}
