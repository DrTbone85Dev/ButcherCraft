package com.butchercraft.machine.packaging;

import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.workstation.block.AbstractInventoryWorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Inventory-only Packaging Table block entity for the v0.8.0 workstation foundation.
 *
 * <p>This block entity persists and synchronizes its slots, exposes the shared item-handler
 * capability, and intentionally performs no packaging recipes, transformations, or product mutation.</p>
 */
public final class PackagingTableBlockEntity extends AbstractInventoryWorkstationBlockEntity {
    public PackagingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(
                ModBlockEntityTypes.PACKAGING_TABLE.get(),
                pos,
                blockState,
                PackagingTableWorkstation.capability()
        );
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PackagingTableBlockEntity blockEntity) {
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
