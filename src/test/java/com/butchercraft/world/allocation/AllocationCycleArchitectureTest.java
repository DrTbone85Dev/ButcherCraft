package com.butchercraft.world.allocation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCycleArchitectureTest {
    @Test
    void workingLedgerDoesNotExposeMutableBranchOperationsPublicly() {
        assertFalse(Modifier.isPublic(WorkingCapacityLedger.Branch.class.getModifiers()));
        for (var method : WorkingCapacityLedger.Branch.class.getDeclaredMethods()) {
            assertFalse(
                    Modifier.isPublic(method.getModifiers()),
                    () -> "Mutable ledger method escaped publicly: " + method
            );
        }
    }

    @Test
    void m22cIntroducesNoSchedulerStageProviderPersistenceOrIntegrationOwner()
            throws IOException {
        Path allocation = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/allocation"
        );
        List<String> forbidden = List.of(
                "butchercraft:allocation_cycle\"",
                "executionOrder() == 350",
                "SavedData",
                "Persistence",
                "SnapshotProvider",
                "ResourceProvider",
                "CapacityProvider",
                "WorkHandler",
                "WorkSubmission",
                "ProductionRun",
                "PlanningCycleDefinition",
                "InventoryManager",
                "TransactionExecutor"
        );
        try (var files = Files.walk(allocation)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
            assertTrue(
                    violations.isEmpty(),
                    () -> "M22C architecture boundary violations: " + violations
            );
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
