package com.butchercraft.workstation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationRemovalLifecycleTest {
    @Test
    void developmentWorkstationRemovalAlwaysReachesSuperclassRemoval() throws IOException {
        String source = source("src/main/java/com/butchercraft/workstation/block/ProcessingWorkstationBlock.java");

        assertTrue(source.contains("blockEntity.dropContents(level, pos)"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("super.onRemove(state, level, pos, newState, movedByPiston);"));
    }

    @Test
    void grinderRemovalAlwaysReachesSuperclassRemoval() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlock.java");

        assertTrue(source.contains("blockEntity.dropContents(level, pos)"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("super.onRemove(state, level, pos, newState, movedByPiston);"));
    }

    @Test
    void bandsawRemovalRoutesDropsThroughLowerAndStillReachesSuperclassRemoval() throws IOException {
        String lower = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawBlock.java");
        String upper = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawUpperBlock.java");

        assertTrue(lower.contains("blockEntity.dropContents(level, pos)"));
        assertTrue(lower.contains("level.removeBlock(upperPos, false)"));
        assertTrue(lower.contains("finally"));
        assertTrue(lower.contains("super.onRemove(state, level, pos, newState, movedByPiston);"));
        assertTrue(upper.contains("level.destroyBlock(pos.below(), true)"));
        assertTrue(upper.contains("finally"));
        assertTrue(upper.contains("super.onRemove(state, level, pos, newState, movedByPiston);"));
    }

    @Test
    void packagingTableRemovalDropsInventoryAndStillReachesSuperclassRemoval() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/packaging/PackagingTableBlock.java");

        assertTrue(source.contains("blockEntity.dropContents(level, pos)"));
        assertTrue(source.contains("finally"));
        assertTrue(source.contains("super.onRemove(state, level, pos, newState, movedByPiston);"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
