package com.butchercraft.world.checkpoint;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointRecoveryDependencyBoundaryTest {
    @Test
    void checkpointRecoveryFoundationRemainsPureMetadataAndIndependentOfRuntimeOwners()
            throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/checkpoint");
        List<String> forbidden = List.of(
                "net.minecraft",
                "net.neoforged",
                "com.butchercraft.world.planning",
                "com.butchercraft.world.production",
                "com.butchercraft.world.simulation.scheduler",
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
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Checkpoint Recovery boundary violations: " + violations);
        }
    }

    @Test
    void checkpointOwnerIntegrationIntroducesNoAutomaticRuntimeService()
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
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Deferred checkpoint owners were added: " + violations);
        }
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
