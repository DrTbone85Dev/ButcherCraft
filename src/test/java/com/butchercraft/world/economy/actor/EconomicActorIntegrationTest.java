package com.butchercraft.world.economy.actor;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicActorIntegrationTest {
    @Test
    void modRegistersEconomicActorLifecycleAfterGoodsLifecycle() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("EconomicActorService.INSTANCE::initialize"));
        assertTrue(source.contains("EconomicActorService.INSTANCE::save"));
        assertTrue(source.indexOf("GoodService.INSTANCE::initialize")
                < source.indexOf("EconomicActorService.INSTANCE::initialize"));
    }

    @Test
    void serviceUsesGoodsAndWorldScopedDefinitionPersistence() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/EconomicActorService.java"
        ));

        assertTrue(source.contains("GoodService"));
        assertTrue(source.contains("goodService.managerFor(server)"));
        assertTrue(source.contains("EconomicActorSchema.FILE_NAME"));
        assertTrue(source.contains("LevelResource.ROOT"));
        assertTrue(source.contains("ServerStartedEvent"));
        assertTrue(source.contains("ServerStoppingEvent"));
    }
}
