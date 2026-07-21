package com.butchercraft.workstation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationMenuLayoutTest {
    @Test
    void sharedMenuUsesConfiguredInventorySizeForMachineAndPlayerBoundaries() throws IOException {
        String source = source("src/main/java/com/butchercraft/workstation/menu/ProcessingWorkstationMenu.java");

        assertTrue(source.contains("this.workstationSlotCount = inventory.totalSlotCount();"));
        assertTrue(source.contains("this.playerInventoryStart = workstationSlotCount;"));
        assertTrue(source.contains("this.playerInventoryEnd = playerInventoryStart + 27;"));
        assertTrue(source.contains("this.hotbarEnd = playerInventoryEnd + 9;"));
        assertTrue(source.contains("for (int inputIndex = 0; inputIndex < inventory.inputSlotCount(); inputIndex++)"));
        assertTrue(source.contains("addSlot(new SlotItemHandler(inventory, slot, workstationSlotX(slot), workstationSlotY(slot)))"));
        assertTrue(source.contains("for (int outputIndex = 0; outputIndex < inventory.outputSlotCount(); outputIndex++)"));
        assertTrue(source.contains("int slot = inventory.firstOutputSlot() + outputIndex;"));
        assertTrue(source.contains("if (index < workstationSlotCount)"));
        assertTrue(source.contains("moveItemStackTo(original, inventory.firstInputSlot(), inventory.firstOutputSlot(), false)"));
        assertFalse(source.contains("WorkstationInventory.SLOT_COUNT"));
        assertFalse(source.contains("WorkstationInventory.OUTPUT_SLOT_COUNT"));
        assertFalse(source.contains("WorkstationInventory.isOutputSlot"));
    }

    @Test
    void clientConstructorsUseTheSameMachineCapabilitiesAsServerConstructors() throws IOException {
        String development = source("src/main/java/com/butchercraft/workstation/menu/ProcessingWorkstationMenu.java");
        String grinder = source("src/main/java/com/butchercraft/machine/grinder/GrinderMenu.java");
        String bandsaw = source("src/main/java/com/butchercraft/machine/bandsaw/BandsawMenu.java");
        String packaging = source("src/main/java/com/butchercraft/machine/packaging/PackagingTableMenu.java");

        assertTrue(development.contains("DevelopmentWorkstationFixtures.capability()"));
        assertTrue(grinder.contains("GrinderWorkstation.capability()"));
        assertTrue(bandsaw.contains("BandsawWorkstation.capability()"));
        assertTrue(packaging.contains("PackagingTableWorkstation.capability()"));
    }

    @Test
    void screensRenderOnlyTheConfiguredOutputSlotCount() throws IOException {
        String source = source("src/main/java/com/butchercraft/client/screen/AbstractProcessingWorkstationScreen.java");

        assertTrue(source.contains("outputIndex < menu.outputSlotCount()"));
        assertTrue(source.contains("inputIndex < menu.inputSlotCount()"));
        assertFalse(source.contains("outputIndex < 8"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
