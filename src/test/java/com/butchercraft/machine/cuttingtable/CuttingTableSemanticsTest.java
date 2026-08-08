package com.butchercraft.machine.cuttingtable;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.workstation.WorkstationCapability;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuttingTableSemanticsTest {
    @Test
    void acceptedRecipeUsesOneInputAndTwoDistinctOutputs() {
        WorkstationCapability capability = CuttingTableWorkstation.capability();

        assertEquals(1, capability.inputSlots());
        assertEquals(2, capability.outputSlots());
        assertTrue(capability.manualPlayerOperationAllowed());
        assertFalse(capability.automatedEmployeeOperationMayBeSupportedLater());
    }

    @Test
    void beefTrimParticipatesOnlyThroughTheOutputEndpoint() throws IOException {
        String blockEntity = source("src/main/java/com/butchercraft/machine/cuttingtable/"
                + "CuttingTableBlockEntity.java");
        String endpointService = source("src/main/java/com/butchercraft/workstation/endpoint/runtime/"
                + "WorkstationEndpointService.java");

        assertTrue(blockEntity.contains("trimOutputSlot()"));
        assertTrue(blockEntity.contains("inventory().firstOutputSlot() + 1"));
        assertTrue(blockEntity.contains("exactStack.is(ModItems.BEEF_TRIM.get())"));
        assertTrue(blockEntity.contains("preloadOutputForDevelopment"));
        assertTrue(endpointService.contains("record.slotIndex() != observation.slotIndex()"));
        assertTrue(endpointService.contains("endpoint.endpointSlotIndex(kind) != slotIndex"));
        assertFalse(endpointService.contains("endpointAccepts(kind, 0, stack)"));
        assertFalse(blockEntity.contains("ExecutionService"));
        assertFalse(blockEntity.contains("SimulationSchedulerService"));
        assertTrue(blockEntity.contains("CuttingTableExecutionCoordinator.INSTANCE"));
    }

    @Test
    void screenLabelsBothInventoryRolesAndNormalOutputInsertionRemainsBlocked() throws IOException {
        String screen = source("src/main/java/com/butchercraft/client/screen/CuttingTableScreen.java");
        String menu = source("src/main/java/com/butchercraft/workstation/menu/ProcessingWorkstationMenu.java");
        String language = source("src/main/java/com/butchercraft/data/ButcherCraftLanguageProvider.java");
        String capabilities = source("src/main/java/com/butchercraft/registration/ModCapabilities.java");

        assertTrue(screen.contains("screen.butchercraft.cutting_table.input"));
        assertTrue(screen.contains("screen.butchercraft.cutting_table.primary_output"));
        assertTrue(screen.contains("screen.butchercraft.cutting_table.trim_output"));
        assertTrue(language.contains("\"Primary Output\""));
        assertTrue(language.contains("\"Beef Trim Output\""));
        assertTrue(menu.contains("private static final class OutputSlot"));
        assertTrue(menu.contains("public boolean mayPlace(ItemStack stack)"));
        assertTrue(menu.contains("return false;"));
        assertFalse(capabilities.contains("ModBlockEntityTypes.CUTTING_TABLE"));
    }

    @Test
    void employeeFabricationAndDirectInventoryShortcutsRemainGated() throws IOException {
        String blockEntity = source("src/main/java/com/butchercraft/machine/cuttingtable/"
                + "CuttingTableBlockEntity.java");
        String employeeOperation = source("src/main/java/com/butchercraft/integration/employee/"
                + "EmployeeWorkstationOperationService.java");

        assertFalse(employeeOperation.contains("CuttingTableBlockEntity"));
        assertFalse(blockEntity.contains("ProductionService"));
        assertFalse(blockEntity.contains("EmployeeService"));
        assertFalse(blockEntity.contains("MaterialHandlingService"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(path));
    }
}
