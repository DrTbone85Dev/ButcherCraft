package com.butchercraft.world.checkpoint;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointRecoveryDependencyBoundaryTest {
    private static final List<String> AUTHORIZED_RUNTIME_INTEGRATION_FILES = List.of(
            "LiveCheckpointParticipantRegistry.java",
            "LiveCheckpointService.java",
            "LivePlatformDeterminismManifest.java",
            "CheckpointPublicationTiming.java",
            "StartupMutationGateService.java",
            "StartupRecoveryService.java"
    );

    @Test
    void checkpointRecoveryFoundationRemainsPureMetadataAndIndependentOfRuntimeOwners()
            throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/checkpoint");
        List<String> forbidden = List.of(
                "net.minecraft",
                "net.neoforged",
                "com.butchercraft.world.production",
                "com.butchercraft.world.inventory",
                "com.butchercraft.world.transaction",
                "com.butchercraft.world.allocation",
                "com.butchercraft.world.execution",
                "com.butchercraft.world.evidence",
                "com.butchercraft.world.identity",
                "SavedData",
                "WorldIdentitySavedData",
                "TransactionManager",
                "InventoryManager",
                "PlanningManager",
                "ProductionManager",
                "SimulationScheduler",
                "System.currentTimeMillis",
                "System.nanoTime",
                "java.time.",
                "Clock.system",
                "java.util.Random",
                "RandomGenerator",
                "ThreadLocalRandom",
                "java.lang.reflect"
        );

        try (var files = Files.walk(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !AUTHORIZED_RUNTIME_INTEGRATION_FILES.contains(path.getFileName().toString()))
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Checkpoint Recovery boundary violations: " + violations);
        }
    }

    @Test
    void splitSnapshotAnalysisDependsOnlyOnImmutableOwnerRecoveryEvidence() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/checkpoint");
        List<String> allowedOwnerImports = List.of(
                "import com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock;",
                "import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAcknowledgement;",
                "import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAssessment;",
                "import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;",
                "import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryDiscontinuity;"
        );
        try (var files = Files.walk(root)) {
            List<String> disallowed = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !AUTHORIZED_RUNTIME_INTEGRATION_FILES.contains(path.getFileName().toString()))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> line.startsWith("import com.butchercraft.world.planning")
                                            || line.startsWith("import com.butchercraft.world.simulation.scheduler"))
                                    .filter(line -> !allowedOwnerImports.contains(line))
                                    .map(line -> path + ": " + line);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertTrue(disallowed.isEmpty(), () -> "Checkpoint Recovery owner import violations: " + disallowed);
        }
    }

    @Test
    void onlyOwnerAuthorizedCheckpointRuntimeServicesExist()
            throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/checkpoint");
        List<String> forbiddenNames = List.of(
                "Migration",
                "SavedData",
                "Command",
                "Service",
                "Manager"
        );

        try (var files = Files.list(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> forbiddenNames.stream()
                            .anyMatch(name -> path.getFileName().toString().contains(name)))
                    .filter(path -> !path.getFileName().toString()
                            .equals("LegacySplitRecoveryPublicationService.java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("LiveCheckpointService.java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("StartupMutationGateService.java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("StartupRecoveryService.java"))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Deferred checkpoint owners were added: " + violations);
        }
    }

    @Test
    void recoveryOwnerPreparersCannotImportMutableRuntimeAuthorities() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft");
        List<String> forbiddenImportFragments = List.of(
                "Manager",
                "Service",
                "Handler",
                "Controller",
                "Registry",
                "SavedData",
                "net.minecraft",
                "net.neoforged"
        );
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path path : files
                    .filter(candidate -> candidate.getFileName().toString()
                            .endsWith("LegacyRecoveryOwnerPreparer.java"))
                    .toList()) {
                Files.readAllLines(path).stream()
                        .filter(line -> line.startsWith("import "))
                        .filter(line -> forbiddenImportFragments.stream().anyMatch(line::contains))
                        .map(line -> path + ": " + line)
                        .forEach(violations::add);
            }
        }
        assertTrue(violations.isEmpty(), () -> "Recovery owner preparer runtime imports: " + violations);
    }

    @Test
    void ownerNativeRestorationCannotInvokeConsequentialRuntimeHandlers() throws IOException {
        List<Path> restorationSources = List.of(
                TestProjectPaths.projectPath(
                        "src/main/java/com/butchercraft/world/checkpoint/OwnerNativeRestorationCoordinator.java"),
                TestProjectPaths.projectPath(
                        "src/main/java/com/butchercraft/integration/checkpoint/NativeOwnerRestorationAdapters.java"),
                TestProjectPaths.projectPath(
                        "src/main/java/com/butchercraft/integration/checkpoint/FileBundleNativeRestorationAdapter.java"),
                TestProjectPaths.projectPath(
                        "src/main/java/com/butchercraft/world/simulation/checkpoint/SimulationClockNativeRestorationAdapter.java"),
                TestProjectPaths.projectPath(
                        "src/main/java/com/butchercraft/world/simulation/scheduler/checkpoint/SimulationSchedulerNativeRestorationAdapter.java")
        );
        List<String> forbiddenInvocations = List.of(
                "MaterialHandlingService.INSTANCE",
                "TransactionService.INSTANCE",
                "ProductionService.INSTANCE",
                "EconomicPlanningService.INSTANCE",
                "WorkstationController",
                ".requestExplicitTransfer(",
                ".requestProduction(",
                ".submitTransaction(",
                ".executeCycle(",
                ".advance("
        );

        List<String> violations = new ArrayList<>();
        for (Path source : restorationSources) {
            String content = Files.readString(source);
            forbiddenInvocations.stream()
                    .filter(content::contains)
                    .map(value -> source + ": " + value)
                    .forEach(violations::add);
        }
        assertTrue(violations.isEmpty(), () -> "Restoration consequence-replay paths: " + violations);
    }

    private static boolean containsAny(Path path, List<String> forbidden) {
        try {
            String content = Files.readString(path);
            return forbidden.stream().anyMatch(content::contains);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
