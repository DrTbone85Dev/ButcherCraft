package com.butchercraft.machine.pattyformer;

import com.butchercraft.machine.pattyformer.execution.PattyFormerExecutionCoordinator;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationExecutionEffectResult;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationOperationStartPolicy;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectId;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
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

public final class PattyFormerBlockEntity extends AbstractProcessingWorkstationBlockEntity
        implements WorkstationTransferEndpoint {
    public PattyFormerBlockEntity(BlockPos pos, BlockState blockState) {
        super(
                ModBlockEntityTypes.PATTY_FORMER.get(),
                pos,
                blockState,
                PattyFormerWorkstation.capability(),
                new WorkstationOperationResolver(),
                DevelopmentProductItemMappings.fixtureMapping(),
                WorkstationExecutionStrategy.transformation(),
                PattyFormerExecutionCoordinator.INSTANCE,
                WorkstationOperationStartPolicy.EXPLICIT_REQUEST
        );
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PattyFormerBlockEntity blockEntity) {
        AbstractProcessingWorkstationBlockEntity.serverTick(level, pos, state, blockEntity);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.butchercraft.patty_former");
    }

    public WorkstationExecutionEffectResult completeScheduledExecution(
            ExecutionOperationId operationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            long authoritativeTick
    ) {
        return super.completeScheduledExecution(operationId, domainEffectIdentity, authoritativeTick);
    }

    public WorkstationProductionRequestResult requestPlayerProcessing(WorkstationTickContext tickContext) {
        return requestProductionProcessing(tickContext);
    }

    @Override
    public String endpointTypeIdentity() {
        return PattyFormerWorkstation.ID.toString();
    }

    @Override
    public String endpointOperationStateIdentity() {
        return "butchercraft:patty_former/"
                + workstationState().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String endpointPostOperationStateIdentity(WorkstationEndpointEffectKind kind) {
        return kind == WorkstationEndpointEffectKind.DESTINATION_DEPOSIT
                ? "butchercraft:patty_former/ready"
                : endpointOperationStateIdentity();
    }

    @Override
    public WorkstationEndpointProjection endpointProjection() {
        return endpointProjectionView();
    }

    @Override
    public int endpointSlotIndex(WorkstationEndpointEffectKind kind) {
        return kind == WorkstationEndpointEffectKind.DESTINATION_DEPOSIT
                ? inventory().firstInputSlot()
                : -1;
    }

    @Override
    public ItemStack endpointStackSnapshot(int slotIndex) {
        return endpointStackSnapshotView(slotIndex);
    }

    @Override
    public boolean endpointAccepts(WorkstationEndpointEffectKind kind, int slotIndex, ItemStack exactStack) {
        return kind == WorkstationEndpointEffectKind.DESTINATION_DEPOSIT
                && workstationState() == com.butchercraft.workstation.WorkstationState.IDLE
                && exactStack.is(ModItems.GROUND_BEEF.get())
                && exactStack.getCount() == 1
                && endpointAcceptsView(kind, slotIndex, exactStack);
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
            throw new IllegalStateException("Patty Former rejected the committed transfer endpoint effect");
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
        return new PattyFormerMenu(containerId, playerInventory, this);
    }
}
