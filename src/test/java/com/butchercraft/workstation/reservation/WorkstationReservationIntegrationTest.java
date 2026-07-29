package com.butchercraft.workstation.reservation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationIntegrationTest {
    @Test
    void serviceIsRegisteredAndUsesDedicatedPersistenceFile() throws IOException {
        String butchercraft = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"
        ));
        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/WorkstationReservationService.java"
        ));

        assertTrue(butchercraft.contains("WorkstationReservationService.INSTANCE::initialize"));
        assertTrue(butchercraft.contains("WorkstationReservationService.INSTANCE::save"));
        assertTrue(service.contains("WorkstationReservationSchema.FILE_NAME"));
        assertTrue(service.contains("LevelResource.ROOT"));
    }

    @Test
    void reservationsUseExistingGrinderAndPattyFormerIdentityModels() throws IOException {
        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/WorkstationReservationService.java"
        ));

        assertTrue(service.contains("GrinderWorkstationReference.of"));
        assertTrue(service.contains("PattyFormerWorkstationReference.of"));
        assertTrue(service.contains("GrinderBlock.FACING"));
        assertTrue(service.contains("PattyFormerBlock.FACING"));
    }

    @Test
    void reservationFoundationDoesNotDispatchProductionSchedulerOrExecution() throws IOException {
        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/WorkstationReservationService.java"
        ));
        String entity = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/entity/employee/EmployeeEntity.java"
        ));

        assertTrue(!service.contains("ProductionService"));
        assertTrue(!service.contains("SimulationSchedulerService"));
        assertTrue(!service.contains("ExecutionService"));
        assertTrue(!service.contains("requestProductionProcessing"));
        assertTrue(!entity.contains("openMenu"));
        assertTrue(!entity.contains("insertItem"));
        assertTrue(!entity.contains("extractItem"));
    }

    @Test
    void diagnosticsExposeWorkstationReservationCommandsWithBuiltInArgumentTypes() throws IOException {
        String diagnostics = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java"
        ));

        assertTrue(diagnostics.contains("Commands.literal(\"assign-workstation\")"));
        assertTrue(diagnostics.contains("Commands.literal(\"release-workstation\")"));
        assertTrue(diagnostics.contains("Commands.literal(\"workstation\")"));
        assertTrue(diagnostics.contains("Commands.literal(\"reservations\")"));
        assertTrue(diagnostics.contains("StringArgumentType.greedyString()"));
        assertTrue(!diagnostics.contains("EmployeeReferenceArgumentType"));
    }
}
