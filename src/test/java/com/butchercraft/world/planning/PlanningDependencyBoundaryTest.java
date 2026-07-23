package com.butchercraft.world.planning;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningDependencyBoundaryTest {
    @Test
    void planningDomainContainsNoMinecraftNeoForgeClientOrWallClockDependencies() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/planning");
        List<String> forbidden = List.of(
                "net.minecraft", "net.neoforged", "ItemStack", "java.time",
                "System.currentTimeMillis", "System.nanoTime"
        );
        try (var files = Files.walk(root)) {
            List<Path> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, forbidden)).toList();
            assertTrue(violations.isEmpty(), () -> "Planning dependency boundary violations: " + violations);
        }
    }

    @Test
    void pipelineDoesNotMutateAuthoritativeSubsystemsDirectly() throws Exception {
        String pipeline = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/planning/PlanningPipeline.java"));

        assertFalse(pipeline.contains("inventoryManager().apply"));
        assertFalse(pipeline.contains("inventoryManager().add"));
        assertFalse(pipeline.contains("transactionManager().submit"));
        assertFalse(pipeline.contains("orderManager().recordFulfillment"));
        assertFalse(pipeline.contains("productionManager().execute"));
        assertTrue(pipeline.contains("submissionAdapter.submit"));
    }

    private static boolean containsAny(Path file, List<String> forbidden) {
        try {
            String content = Files.readString(file);
            return forbidden.stream().anyMatch(content::contains);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
