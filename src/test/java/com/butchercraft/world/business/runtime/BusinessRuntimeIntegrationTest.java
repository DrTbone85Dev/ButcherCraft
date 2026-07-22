package com.butchercraft.world.business.runtime;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessRuntimeIntegrationTest {
    @Test
    void modRegistersBusinessRuntimeLifecycleHandlers() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("BusinessRuntimeService.INSTANCE::initialize"));
        assertTrue(source.contains("BusinessRuntimeService.INSTANCE::save"));
    }

    @Test
    void runtimeServiceUsesServerStartStopPersistenceAndSimulationEvents() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/BusinessRuntimeService.java"
        ));

        assertTrue(source.contains("ServerStartedEvent"));
        assertTrue(source.contains("ServerStoppingEvent"));
        assertTrue(source.contains("BusinessRuntimeSchema.FILE_NAME"));
        assertTrue(source.contains("LevelResource.ROOT"));
        assertTrue(source.contains("SimulationEventType.DAILY_ROLLOVER"));
        assertTrue(source.contains("SimulationEventType.WEEKLY_ROLLOVER"));
    }
}
