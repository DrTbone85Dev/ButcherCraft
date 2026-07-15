package com.butchercraft.product;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductIntegrationDependencyTest {
    @Test
    void engineSourcesStillDoNotImportMinecraftOrNeoForge() throws IOException {
        List<Path> offenders = sourceFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/engine")).stream()
                .filter(path -> sourceContains(path, "import net.minecraft") || sourceContains(path, "import net.neoforged"))
                .toList();

        assertTrue(offenders.isEmpty(), "Engine sources must stay Minecraft-independent: " + offenders);
    }

    @Test
    void minecraftProductIntegrationIsOutsideEnginePackage() throws IOException {
        Path engineRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/engine");
        List<Path> integrationFiles = sourceFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/product")).stream()
                .filter(path -> sourceContains(path, "net.minecraft"))
                .toList();
        List<Path> misplaced = integrationFiles.stream()
                .filter(path -> path.normalize().startsWith(engineRoot))
                .toList();

        assertTrue(!integrationFiles.isEmpty(), "Product integration sources should be present outside the engine package");
        assertTrue(misplaced.isEmpty(), "Minecraft integration must not be placed under the engine package: " + misplaced);
    }

    @Test
    void commonSourceDoesNotImportMinecraftClientClasses() throws IOException {
        List<Path> offenders = sourceFiles(TestProjectPaths.projectPath("src/main/java")).stream()
                .filter(path -> sourceContains(path, "import net.minecraft.client"))
                .toList();

        assertTrue(offenders.isEmpty(), "Common sources must not import Minecraft client classes: " + offenders);
    }

    private static List<Path> sourceFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static boolean sourceContains(Path path, String value) {
        try {
            return Files.readString(path).contains(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan " + path, exception);
        }
    }
}
