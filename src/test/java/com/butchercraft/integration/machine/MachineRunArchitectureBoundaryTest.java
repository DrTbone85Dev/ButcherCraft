package com.butchercraft.integration.machine;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MachineRunArchitectureBoundaryTest {
    @Test
    void workstationOperatingOwnerDoesNotAcquireExecutionRuntimeOrOtherSubsystemAuthority() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/workstation/operation");
        List<String> forbidden = List.of(
                "import com.butchercraft.world.ExecutionService",
                "import com.butchercraft.world.ExecutionMachineRunService",
                "import com.butchercraft.world.execution.ExecutionManager",
                "import com.butchercraft.world.simulation.scheduler.",
                "import com.butchercraft.world.production.",
                "import com.butchercraft.world.workforce.",
                "import com.butchercraft.world.materialhandling.",
                "import net.minecraft.",
                "import net.neoforged."
        );

        assertSourcesExclude(root, forbidden);
    }

    @Test
    void crossOwnerCoordinatorDoesNotPersistOrOwnCanonicalRuntimeState() throws IOException {
        Path file = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/integration/machine/MachineRunCoordinatorService.java"
        );
        String source = Files.readString(file);

        for (String forbidden : List.of(
                "AtomicReference",
                "MachineRunStorage",
                "MachineOperatingStorage",
                "new MachineRunRegistry",
                "new MachineOperatingRegistry"
        )) {
            assertFalse(source.contains(forbidden), () -> file + " must not contain " + forbidden);
        }
    }

    private static void assertSourcesExclude(Path root, List<String> forbidden) throws IOException {
        Path checkpointAdapters = root.resolve("checkpoint");
        try (var files = Files.walk(root)) {
            for (Path file : files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(checkpointAdapters))
                    .toList()) {
                String source = Files.readString(file);
                for (String value : forbidden) {
                    assertFalse(source.contains(value), () -> file + " must not contain " + value);
                }
            }
        }
    }
}
