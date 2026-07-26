package com.butchercraft.world.evidence;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceLifecycleDependencyBoundaryTest {
    @Test
    void evidenceLifecycleFoundationRemainsPureAndIndependentOfFactOwners() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/evidence");
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
                "com.butchercraft.world.checkpoint",
                "SavedData",
                "System.currentTimeMillis",
                "System.nanoTime",
                "java.time.",
                "Clock.system",
                "java.nio.file",
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
            assertTrue(violations.isEmpty(), () -> "Evidence Lifecycle boundary violations: " + violations);
        }
    }

    @Test
    void evidenceLifecycleFoundationIntroducesNoPersistenceArchiveOrRuntimeManagers() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/evidence");
        List<String> forbiddenNames = List.of(
                "Archive",
                "Checkpoint",
                "Recovery",
                "Rollback",
                "Persistence",
                "SavedData",
                "Manager",
                "Service"
        );

        try (var files = Files.list(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> forbiddenNames.stream()
                            .anyMatch(name -> path.getFileName().toString().contains(name)))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Deferred Evidence Lifecycle owners were added: " + violations);
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
