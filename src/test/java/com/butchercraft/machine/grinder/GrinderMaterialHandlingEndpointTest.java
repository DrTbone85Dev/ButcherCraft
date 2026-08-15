package com.butchercraft.machine.grinder;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderMaterialHandlingEndpointTest {
    @Test
    void grinderUsesItsOutputForOneUnitGroundBeefWithdrawalAndReturn() throws IOException {
        String grinder = source("src/main/java/com/butchercraft/machine/grinder/GrinderBlockEntity.java");

        assertTrue(grinder.contains("case SOURCE_WITHDRAWAL, SOURCE_RETURN -> inventory().firstOutputSlot()"));
        assertTrue(grinder.contains("exactStack.is(ModItems.GROUND_BEEF.get())"));
        assertTrue(grinder.contains("exactStack.getCount() != 1"));
        assertTrue(grinder.contains("case SOURCE_WITHDRAWAL, SOURCE_RETURN -> \"butchercraft:grinder/idle\""));
        assertFalse(grinder.contains("case SOURCE_WITHDRAWAL, SOURCE_RETURN -> inventory().firstInputSlot()"));
    }

    @Test
    void schemaOneRejectsPartialWithdrawalWithoutChangingSlotOrJournalSemantics() throws IOException {
        String inventory = source("src/main/java/com/butchercraft/workstation/WorkstationInventory.java");
        String endpoint = source("src/main/java/com/butchercraft/workstation/endpoint/runtime/"
                + "WorkstationEndpointService.java");
        String projection = source("src/main/java/com/butchercraft/workstation/block/"
                + "AbstractInventoryWorkstationBlockEntity.java");
        String journal = source("src/main/java/com/butchercraft/workstation/endpoint/"
                + "WorkstationEndpointJournalRecord.java");

        assertTrue(inventory.contains("public int getSlotLimit(int slot)"));
        assertTrue(inventory.contains("return slotCapacityPolicy.capacity(slot);"));
        assertTrue(inventory.contains("WorkstationSlotCapacityPolicy.uniform"));
        assertTrue(endpoint.contains("WorkstationEndpointResultCode.UNSUPPORTED_SOURCE_COUNT"));
        assertTrue(endpoint.contains("Schema 1 transfers require exactly one source item"));
        assertTrue(projection.contains("case SOURCE_WITHDRAWAL ->"));
        assertTrue(projection.contains("clearOutputSlotsInternal"));
        assertTrue(journal.contains("? WorkstationEndpointFreshnessIdentity.EMPTY_SLOT_CONTENT_DIGEST"));
        assertFalse(journal.contains("remainingStack"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(path));
    }
}
