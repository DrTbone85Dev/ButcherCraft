package com.butchercraft.workstation.block;

import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.workstation.DevelopmentWorkstationFixtures;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class ProcessingWorkstationBlockEntity extends AbstractProcessingWorkstationBlockEntity {
    public ProcessingWorkstationBlockEntity(BlockPos pos, BlockState blockState) {
        super(
                ModBlockEntityTypes.DEVELOPMENT_PROCESSING_WORKSTATION.get(),
                pos,
                blockState,
                DevelopmentWorkstationFixtures.capability(),
                new WorkstationOperationResolver(),
                DevelopmentProductItemMappings.fixtureMapping()
        );
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessingWorkstationBlockEntity blockEntity) {
        AbstractProcessingWorkstationBlockEntity.serverTick(level, pos, state, blockEntity);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.butchercraft.development_processing_workstation");
    }

    @Nullable
    @Override
    protected AbstractContainerMenu createWorkstationMenu(int containerId, Inventory playerInventory, Player player) {
        return new ProcessingWorkstationMenu(containerId, playerInventory, this);
    }
}
