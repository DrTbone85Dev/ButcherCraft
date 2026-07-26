package com.butchercraft.machine;

import com.butchercraft.registration.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MachineRenderingShapeTest {
    @Test
    void nonFullMachineModelsDoNotOccludeNeighborFaces() {
        assertFalse(ModBlocks.GRINDER.get().defaultBlockState().canOcclude());
        assertFalse(ModBlocks.PATTY_FORMER.get().defaultBlockState().canOcclude());
        assertFalse(ModBlocks.BANDSAW.get().defaultBlockState().canOcclude());
        assertFalse(ModBlocks.BANDSAW_UPPER.get().defaultBlockState().canOcclude());
    }

    @Test
    void nonFullMachineModelsKeepExistingFullBlockSelectionAndCollisionShapes() {
        assertFullBlockShapes(ModBlocks.GRINDER.get().defaultBlockState());
        assertFullBlockShapes(ModBlocks.PATTY_FORMER.get().defaultBlockState());
        assertFullBlockShapes(ModBlocks.BANDSAW.get().defaultBlockState());
        assertFullBlockShapes(ModBlocks.BANDSAW_UPPER.get().defaultBlockState());
    }

    private static void assertFullBlockShapes(BlockState state) {
        assertFullBlockBounds(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertFullBlockBounds(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    private static void assertFullBlockBounds(VoxelShape shape) {
        AABB bounds = shape.bounds();
        assertEquals(0.0D, bounds.minX);
        assertEquals(0.0D, bounds.minY);
        assertEquals(0.0D, bounds.minZ);
        assertEquals(1.0D, bounds.maxX);
        assertEquals(1.0D, bounds.maxY);
        assertEquals(1.0D, bounds.maxZ);
    }
}
