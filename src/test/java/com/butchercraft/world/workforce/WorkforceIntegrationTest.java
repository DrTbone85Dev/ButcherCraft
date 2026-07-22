package com.butchercraft.world.workforce;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkforceIntegrationTest {
    @Test
    void modRegistersWorkforceLifecycleHandlers() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("WorkforceService.INSTANCE::initialize"));
        assertTrue(source.contains("WorkforceService.INSTANCE::save"));
    }

    @Test
    void workforceServiceUsesServerStartStopPersistenceAndBusinessRuntime() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/WorkforceService.java"));

        assertTrue(source.contains("ServerStartedEvent"));
        assertTrue(source.contains("ServerStoppingEvent"));
        assertTrue(source.contains("WorkforceSchema.FILE_NAME"));
        assertTrue(source.contains("LevelResource.ROOT"));
        assertTrue(source.contains("BusinessRuntimeService"));
    }
}
