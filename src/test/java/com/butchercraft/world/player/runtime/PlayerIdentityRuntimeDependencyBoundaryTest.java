package com.butchercraft.world.player.runtime;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerIdentityRuntimeDependencyBoundaryTest {
    @Test
    void onlyJoinInitializerImportsMinecraftOrNeoForgeApis() throws IOException {
        Path runtimePackage = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/player/runtime");
        try (var files = Files.list(runtimePackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("PlayerJoinInitializer.java")) {
                    continue;
                }
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
            }
        }
    }
}
