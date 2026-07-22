package com.butchercraft.world.goods;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodIntegrationTest {
    @Test
    void modRegistersGoodsLifecycleHandlers() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("GoodService.INSTANCE::initialize"));
        assertTrue(source.contains("GoodService.INSTANCE::save"));
    }

    @Test
    void goodServiceUsesServerStartStopPersistenceAndBuiltInIndustryValidation() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/GoodService.java"
        ));

        assertTrue(source.contains("ServerStartedEvent"));
        assertTrue(source.contains("ServerStoppingEvent"));
        assertTrue(source.contains("GoodSchema.FILE_NAME"));
        assertTrue(source.contains("LevelResource.ROOT"));
        assertTrue(source.contains("BuiltInIndustryCatalog.all()"));
    }
}
