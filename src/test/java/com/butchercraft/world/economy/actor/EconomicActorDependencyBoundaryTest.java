package com.butchercraft.world.economy.actor;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EconomicActorDependencyBoundaryTest {
    @Test
    void economicActorPackageDoesNotImportMinecraftNeoForgeOrItemStackApis() throws IOException {
        Path actorPackage = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/economy/actor"
        );
        try (var files = Files.walk(actorPackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
                assertFalse(source.contains("ItemStack"), file + " must not depend on ItemStack");
            }
        }
    }
}
