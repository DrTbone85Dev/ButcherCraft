package com.butchercraft.world.inventory;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryIntegrationTest {
    @Test
    void modRegistersInventoryLifecycleAfterEconomicActors() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("InventoryService.INSTANCE::initialize"));
        assertTrue(source.contains("InventoryService.INSTANCE::save"));
        assertTrue(source.indexOf("EconomicActorService.INSTANCE::initialize")
                < source.indexOf("InventoryService.INSTANCE::initialize"));
    }

    @Test
    void inventoryServiceUsesActorCatalogAndWorldScopedPersistence() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/InventoryService.java"
        ));

        assertTrue(source.contains("EconomicActorService"));
        assertTrue(source.contains("economicActorService.managerFor(server)"));
        assertTrue(source.contains("actorRegistry.goodRegistry()"));
        assertTrue(source.contains("InventorySchema.FILE_NAME"));
        assertTrue(source.contains("LevelResource.ROOT"));
        assertTrue(source.contains("ServerStartedEvent"));
        assertTrue(source.contains("ServerStoppingEvent"));
    }
}
