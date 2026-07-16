package com.butchercraft.machine.grinder;

import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
                DevelopmentProductItemMappings.fixtureMapping()
        );
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrinderBlockEntity blockEntity) {
        AbstractProcessingWorkstationBlockEntity.serverTick(level, pos, state, blockEntity);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.butchercraft.grinder");
    }

    @Nullable
    @Override
    protected AbstractContainerMenu createWorkstationMenu(int containerId, Inventory playerInventory, Player player) {
        return new GrinderMenu(containerId, playerInventory, this);
    }
}
