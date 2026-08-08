package com.butchercraft.machine.cuttingtable.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

public record CuttingTableWorkstationReference(ResourceLocation dimension, BlockPos blockPos) {
    private static final String PREFIX = "workstation/cutting_table";

    public CuttingTableWorkstationReference {
        dimension = Objects.requireNonNull(dimension, "dimension");
        blockPos = Objects.requireNonNull(blockPos, "blockPos").immutable();
        if (dimension.getPath().contains("/")) {
            throw new IllegalArgumentException(
                    "Cutting Table Execution workstation identity does not support nested dimension paths"
            );
        }
    }

    public static CuttingTableWorkstationReference of(ServerLevel level, BlockPos blockPos) {
        return new CuttingTableWorkstationReference(level.dimension().location(), blockPos);
    }

    public String identity() {
        return "butchercraft:" + PREFIX + "/"
                + dimension.getNamespace() + "/"
                + dimension.getPath() + "/"
                + blockPos.getX() + "/"
                + blockPos.getY() + "/"
                + blockPos.getZ();
    }

    public ResourceKey<Level> dimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, dimension);
    }

    public static Optional<CuttingTableWorkstationReference> parse(String identity) {
        ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(identity, "identity"));
        if (id == null || !"butchercraft".equals(id.getNamespace())) {
            return Optional.empty();
        }
        String[] parts = id.getPath().split("/");
        if (parts.length != 7 || !"workstation".equals(parts[0]) || !"cutting_table".equals(parts[1])) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryBuild(parts[2], parts[3]);
        if (dimension == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CuttingTableWorkstationReference(
                    dimension,
                    new BlockPos(
                            Integer.parseInt(parts[4]),
                            Integer.parseInt(parts[5]),
                            Integer.parseInt(parts[6])
                    )
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
