package com.butchercraft.workstation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public record WorkstationTickContext(
        ServerLevel level,
        BlockPos blockPos
) {
    public WorkstationTickContext {
        level = Objects.requireNonNull(level, "level");
        blockPos = Objects.requireNonNull(blockPos, "blockPos").immutable();
    }

    public RegistryAccess registryAccess() {
        return level.registryAccess();
    }
}
