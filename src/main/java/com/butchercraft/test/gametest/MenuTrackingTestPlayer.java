package com.butchercraft.test.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

final class MenuTrackingTestPlayer extends Player {
    private int nextContainerId = 1;

    MenuTrackingTestPlayer(Level level, BlockPos position) {
        super(level, position, 0.0F, new GameProfile(UUID.randomUUID(), "menu-tracking-player"));
    }

    @Override
    public OptionalInt openMenu(
            MenuProvider menuProvider,
            Consumer<RegistryFriendlyByteBuf> extraDataWriter
    ) {
        int containerId = nextContainerId++;
        AbstractContainerMenu menu = menuProvider.createMenu(containerId, getInventory(), this);
        if (menu == null) {
            return OptionalInt.empty();
        }
        containerMenu = menu;
        return OptionalInt.of(containerId);
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return true;
    }
}
