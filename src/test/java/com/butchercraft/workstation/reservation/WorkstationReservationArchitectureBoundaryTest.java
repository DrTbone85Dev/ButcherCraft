package com.butchercraft.workstation.reservation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationArchitectureBoundaryTest {
    @Test
    void workforceServiceOwnsReservationMutationAndUsesWorkforceRecoveryGate() throws IOException {
        String service = source("src/main/java/com/butchercraft/world/WorkstationReservationService.java");
        String materialHandling = source("src/main/java/com/butchercraft/world/EmployeeMaterialHandlingService.java");

        assertTrue(service.contains("new WorkstationReservationManager"));
        assertTrue(service.contains("LegacySplitRecoveryParticipants.WORKFORCE"));
        assertTrue(materialHandling.contains("reservationService.assignMaterialHandler"));
        assertFalse(materialHandling.contains("new WorkstationReservationManager"));
        assertFalse(materialHandling.contains("WorkstationReservationRecord.acquire"));
    }

    @Test
    void reservationAuthorityDoesNotOwnRunInventorySchedulerOrCustodyMutation() throws IOException {
        Path reservationRoot = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/workstation/reservation");
        try (var files = Files.walk(reservationRoot)) {
            var violations = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String value = Files.readString(path);
                            return value.contains("ExecutionService")
                                    || value.contains("MachineRunRegistry")
                                    || value.contains("SimulationSchedulerService")
                                    || value.contains("MaterialHandlingRuntime")
                                    || value.contains("ItemStack")
                                    || value.contains("BlockEntity");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Reservation authority boundary violations: " + violations);
        }
    }

    @Test
    void storageUsesSharedAtomicPublicationAndCurrentCheckpointOwnershipIsSingular() throws IOException {
        String storage = source(
                "src/main/java/com/butchercraft/workstation/reservation/persistence/WorkstationReservationStorage.java");
        String checkpoint = source(
                "src/main/java/com/butchercraft/world/checkpoint/LiveCheckpointParticipantRegistry.java");
        String restoration = source(
                "src/main/java/com/butchercraft/integration/checkpoint/NativeOwnerRestorationAdapters.java");

        assertTrue(storage.contains("AtomicFilePublication.publishUtf8"));
        assertFalse(storage.contains("Files.move("));
        assertTrue(checkpoint.contains("CheckpointOwnerFileSnapshot workforce("));
        assertTrue(checkpoint.contains("files.put(\"workstation_reservations.json\""));
        assertTrue(restoration.contains("CURRENT_WORKFORCE_NATIVE_FILES"));
        assertTrue(restoration.contains("CURRENT_WORKSTATION_NATIVE_FILES"));
    }

    @Test
    void executionAndMachineRunAuthorityDoNotConsumeReservationRolesInIm032a() throws IOException {
        Path executionRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/execution");
        try (var files = Files.walk(executionRoot)) {
            var violations = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String value = Files.readString(path);
                            return value.contains("WorkstationReservationRole")
                                    || value.contains("WorkstationReservationManager");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertTrue(violations.isEmpty(), () -> "IM-032B behavior leaked into Execution: " + violations);
        }
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
