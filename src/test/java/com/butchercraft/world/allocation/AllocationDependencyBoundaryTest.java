package com.butchercraft.world.allocation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationDependencyBoundaryTest {
    @Test
    void allocationDomainRemainsPureAndIndependentOfIntegratedOwners() throws IOException {
        Path root = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/allocation"
        );
        List<String> forbidden = List.of(
                "net.minecraft",
                "net.neoforged",
                "com.butchercraft.world.planning",
                "com.butchercraft.world.production",
                "com.butchercraft.world.simulation.scheduler",
                "com.butchercraft.world.inventory",
                "com.butchercraft.world.transaction",
                "SavedData",
                "AllocationManager",
                "System.currentTimeMillis",
                "System.nanoTime",
                "java.util.Random",
                "ThreadLocalRandom",
                "java.lang.reflect",
                "import java.util.HashMap"
        );

        try (var files = Files.walk(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Allocation boundary violations: " + violations);
        }
    }

    @Test
    void allocationSetRuntimeMutationIsConfinedToItsRuntimeService() throws IOException {
        Path root = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/allocation"
        );
        try (var files = Files.list(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return !name.equals("AllocationSetRuntime.java")
                                && !name.equals("AllocationRuntimeService.java");
                    })
                    .filter(path -> containsAny(path, List.of("AllocationSetRuntime")))
                    .toList();
            assertTrue(
                    violations.isEmpty(),
                    () -> "AllocationSetRuntime escaped its lifecycle owner: " + violations
            );
        }
    }

    @Test
    void allocationDomainIntroducesNoManagerPersistenceCodecProviderOrAlgorithmOwners()
            throws IOException {
        Path root = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/allocation"
        );
        try (var files = Files.list(root)) {
            List<Path> violations = files
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.contains("Manager")
                                || name.contains("Persistence")
                                || name.contains("Codec")
                                || name.contains("SnapshotProvider")
                                || name.contains("ResourceProvider")
                                || name.contains("CapacityProvider")
                                || name.equals("AllocationCycle.java")
                                || name.equals("AllocationAlgorithm.java")
                                || name.equals("CapacityLedger.java")
                                || name.equals("ConflictGraph.java");
                    })
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Deferred allocation owners were added: " + violations);
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
