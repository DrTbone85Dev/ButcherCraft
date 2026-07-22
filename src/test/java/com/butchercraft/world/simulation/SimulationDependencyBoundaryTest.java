package com.butchercraft.world.simulation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SimulationDependencyBoundaryTest {
    @Test
    void onlyClockServiceImportsMinecraftOrNeoForgeApis() throws IOException {
        Path simulationPackage = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/simulation");
        try (var files = Files.list(simulationPackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("SimulationClockService.java")) {
                    continue;
                }
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
            }
        }
    }
}
