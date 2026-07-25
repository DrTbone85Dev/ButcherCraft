package com.butchercraft.world.execution;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ExecutionDependencyBoundaryTest {
    @Test
    void genericExecutionRuntimeDoesNotImportOwnerDomainImplementationsOrMinecraft() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/execution");
        List<String> forbiddenImports = List.of(
                "import com.butchercraft.world.allocation.",
                "import com.butchercraft.world.inventory.",
                "import com.butchercraft.world.transaction.",
                "import com.butchercraft.world.production.",
                "import com.butchercraft.world.planning.",
                "import com.butchercraft.world.checkpoint.",
                "import com.butchercraft.world.evidence.",
                "import net.minecraft.",
                "import net.neoforged."
        );

        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String forbidden : forbiddenImports) {
                    assertFalse(source.contains(forbidden),
                            () -> file + " must not contain " + forbidden);
                }
            }
        }
    }
}
