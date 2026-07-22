package com.butchercraft.world;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentityIntegrationTest {
    @Test
    void modInitializesWorldIdentityServiceOnServerStartedEvent() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("WorldIdentityService.INSTANCE::initialize"));
    }

    @Test
    void worldIdentityServiceUsesOverworldSavedData() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/WorldIdentityService.java"));
        String savedData = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/persistence/WorldIdentitySavedData.java"));

        assertTrue(source.contains("server.overworld()"));
        assertTrue(source.contains("level.getServer().overworld()"));
        assertTrue(savedData.contains("extends SavedData"));
        assertTrue(savedData.contains("butchercraft_world_identity"));
    }
}
