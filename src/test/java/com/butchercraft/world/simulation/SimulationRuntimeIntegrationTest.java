package com.butchercraft.world.simulation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationRuntimeIntegrationTest {
    @Test
    void modRegistersSimulationClockLifecycleHandlers() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("SimulationClockService.INSTANCE::initialize"));
        assertTrue(source.contains("SimulationClockService.INSTANCE::advance"));
        assertTrue(source.contains("SimulationClockService.INSTANCE::save"));
    }

    @Test
    void simulationClockServiceUsesServerStartTickAndStopEvents() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/simulation/SimulationClockService.java"
        ));

        assertTrue(source.contains("ServerStartedEvent"));
        assertTrue(source.contains("ServerTickEvent.Post"));
        assertTrue(source.contains("ServerStoppingEvent"));
        assertTrue(source.contains("SimulationSchema.FILE_NAME"));
        assertTrue(source.contains("LevelResource.ROOT"));
    }
}
