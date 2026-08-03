package com.butchercraft.machine.grinder;

import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.machine.grinder.execution.GrinderExecutionCoordinator;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationExecutionEffectResult;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.execution.ExecutionDomainEffectIdentity;
import com.butchercraft.world.execution.ExecutionOperationId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class GrinderBlockEntity extends AbstractProcessingWorkstationBlockEntity {
    public GrinderBlockEntity(BlockPos pos, BlockState blockState) {
        super(
                ModBlockEntityTypes.GRINDER.get(),
                pos,
                blockState,
                GrinderWorkstation.capability(),
                new WorkstationOperationResolver(),
                DevelopmentProductItemMappings.fixtureMapping(),
                WorkstationExecutionStrategy.transformation(),
                GrinderExecutionCoordinator.INSTANCE
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

    @Nullable
    @Override
    protected AbstractContainerMenu createWorkstationMenu(int containerId, Inventory playerInventory, Player player) {
        return new GrinderMenu(containerId, playerInventory, this);
    }
}
