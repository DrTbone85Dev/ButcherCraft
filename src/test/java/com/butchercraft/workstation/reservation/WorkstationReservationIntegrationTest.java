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
    void navigationQualityStateRemainsTransientAndBounded() throws IOException {
        String entity = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/entity/employee/EmployeeEntity.java"
        ));
        String storage = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/workstation/reservation/persistence/WorkstationReservationStorage.java"
        ));

        assertTrue(entity.contains("PATH_RESTART_COOLDOWN_TICKS"));
        assertTrue(entity.contains("STALL_INTERVAL_TICKS"));
        assertTrue(entity.contains("MAX_RETRIES_PER_CANDIDATE"));
        assertTrue(entity.contains("PATH_SEARCH_NODE_MULTIPLIER"));
        assertTrue(entity.contains("getNextNodeIndex()"));
        assertTrue(entity.contains("distanceToNextPathNodeSquared"));
        assertTrue(entity.contains("usablePathEndpoint"));
        assertTrue(!entity.contains("path.canReach()"));
        assertTrue(entity.contains("getNavigation().moveTo(path, TRAVEL_SPEED)"));
        assertTrue(entity.contains("NavigationRecoveryPhase"));
        assertTrue(entity.contains("NavigationFailureReason"));
        assertTrue(!storage.contains("retryCount"));
        assertTrue(!storage.contains("recoveryPhase"));
        assertTrue(!storage.contains("pathAvailable"));
        assertTrue(!storage.contains("activePathNodeIndex"));
        assertTrue(!storage.contains("lastNodeProgressTick"));
        assertTrue(!storage.contains("lastPathReplacementReason"));
    }

    @Test
    void workstationTargetsExposeRankedApproachCandidatesWithoutBecomingPathfinding() throws IOException {
        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/WorkstationReservationService.java"
        ));

        assertTrue(service.contains("approachCandidates"));
        assertTrue(service.contains("relative(front)"));
        assertTrue(service.contains("relative(left)"));
        assertTrue(service.contains("relative(right)"));
        assertTrue(!service.contains("createPath("));
        assertTrue(!service.contains("moveTo("));
    }

    @Test
    void diagnosticsExposeWorkstationReservationCommandsWithBuiltInArgumentTypes() throws IOException {
        String diagnostics = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java"
        ));

        assertTrue(diagnostics.contains("Commands.literal(\"assign-workstation\")"));
        assertTrue(diagnostics.contains("Commands.literal(\"release-workstation\")"));
        assertTrue(diagnostics.contains("Commands.literal(\"navigation\")"));
        assertTrue(diagnostics.contains("Commands.literal(\"workstation\")"));
        assertTrue(diagnostics.contains("Commands.literal(\"reservations\")"));
        assertTrue(diagnostics.contains("Distance to final destination"));
        assertTrue(diagnostics.contains("Active path node"));
        assertTrue(diagnostics.contains("Distance to next node"));
        assertTrue(diagnostics.contains("Path replacements"));
        assertTrue(diagnostics.contains("StringArgumentType.greedyString()"));
        assertTrue(!diagnostics.contains("EmployeeReferenceArgumentType"));
    }
}
