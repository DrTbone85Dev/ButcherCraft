package com.butchercraft.machine.packaging;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingTableDatagenTest {
    @Test
    void packagingTableResourceProvidersAreRegistered() throws IOException {
        String blockStates = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/data/ButcherCraftBlockStateProvider.java"));
        String lootTables = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/data/ButcherCraftLootTableProvider.java"));
        String language = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/data/ButcherCraftLanguageProvider.java"));

        assertTrue(blockStates.contains("horizontalBlock(ModBlocks.PACKAGING_TABLE.get(), packagingTableModel)"));
        assertTrue(blockStates.contains("simpleBlockItem(ModBlocks.PACKAGING_TABLE.get(), packagingTableModel)"));
        assertTrue(blockStates.contains("models().getBuilder(\"packaging_table\")"));
        assertTrue(blockStates.contains("PACKAGING_TABLE_SURFACE_TEXTURE"));
        assertTrue(blockStates.contains("PACKAGING_TABLE_FRAME_TEXTURE"));
        assertTrue(blockStates.contains("PACKAGING_TABLE_ROLL_TEXTURE"));
        assertTrue(lootTables.contains("dropSelf(ModBlocks.PACKAGING_TABLE.get())"));
        assertTrue(language.contains("Packaging Table"));
        assertTrue(language.contains("container.butchercraft.packaging_table.slot.meat"));
        assertTrue(language.contains("container.butchercraft.packaging_table.slot.tray"));
        assertTrue(language.contains("container.butchercraft.packaging_table.slot.wrap"));
        assertTrue(language.contains("container.butchercraft.packaging_table.slot.result"));
    }
}
