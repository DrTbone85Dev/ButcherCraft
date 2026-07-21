package com.butchercraft.machine.packaging;

import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Packaging Table block entity that executes data-driven retail packaging operations.
 */
public final class PackagingTableBlockEntity extends AbstractProcessingWorkstationBlockEntity {
    public PackagingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(
                ModBlockEntityTypes.PACKAGING_TABLE.get(),
                pos,
                blockState,
                PackagingTableWorkstation.capability(),
                new WorkstationOperationResolver(),
                DevelopmentProductItemMappings.fixtureMapping(),
                new PackagingTableExecutionStrategy()
        );
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PackagingTableBlockEntity blockEntity) {
        WorkstationState previousState = blockEntity.workstationState();
        AbstractProcessingWorkstationBlockEntity.serverTick(level, pos, state, blockEntity);
        if (previousState != WorkstationState.COMPLETE && blockEntity.workstationState() == WorkstationState.COMPLETE) {
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    @Override
    protected boolean canAcceptInput(int slot, ItemStack stack) {
        if (slot == inventory().firstInputSlot()) {
            return canAcceptPrimaryInput(stack);
        }
        return PackagingSupplyItemMappings.isKnownSupplyItem(stack);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.butchercraft.packaging_table");
    }

    @Nullable
    @Override
    protected AbstractContainerMenu createWorkstationMenu(int containerId, Inventory playerInventory, Player player) {
        return new PackagingTableMenu(containerId, playerInventory, this);
    }
}
