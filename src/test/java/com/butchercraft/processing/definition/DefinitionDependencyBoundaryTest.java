package com.butchercraft.processing.definition;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionDependencyBoundaryTest {
    @Test
    void enginePackageStillHasNoMinecraftOrNeoForgeImports() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/engine")).stream()
                .filter(path -> sourceContains(path, "import net.minecraft") || sourceContains(path, "import net.neoforged"))
                .toList();

        assertTrue(offenders.isEmpty(), "Engine sources must stay Minecraft-independent: " + offenders);
    }

    @Test
    void registryAndLoadingClassesRemainOutsideEnginePackage() throws IOException {
        Path engineRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/engine").normalize();
        List<Path> registryAwareFiles = javaFiles(TestProjectPaths.projectPath("src/main/java")).stream()
                .filter(path -> sourceContains(path, "ResourceLocation")
                        || sourceContains(path, "RegistryAccess")
                        || sourceContains(path, "ResourceKey"))
                .filter(path -> path.normalize().startsWith(engineRoot))
                .toList();

        assertTrue(registryAwareFiles.isEmpty(), "Registry-aware classes must stay outside engine: " + registryAwareFiles);
    }

    @Test
    void commonSourceDoesNotImportMinecraftClientClasses() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java")).stream()
                .filter(path -> !isClientSource(path))
                .filter(path -> sourceContains(path, "import net.minecraft.client"))
                .toList();

        assertTrue(offenders.isEmpty(), "Common sources must not import Minecraft client classes: " + offenders);
    }

    @Test
    void processingSelectionDoesNotUseSpeciesSwitches() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java")).stream()
                .filter(path -> sourceContains(path, "switch (species")
                        || sourceContains(path, "switch(species")
                        || sourceContains(path, "case \"butchercraft:beef\"")
                        || sourceContains(path, "case \"butchercraft:poultry\""))
                .toList();

        assertTrue(offenders.isEmpty(), "Processing workflow selection should use profiles, not species switches: " + offenders);
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        assertTrue(Files.isDirectory(root), "Expected source directory " + root);
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static boolean sourceContains(Path path, String value) {
        try {
            return Files.readString(path).contains(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan " + path, exception);
        }
    }

    private static boolean isClientSource(Path path) {
        return path.normalize().toString().replace('\\', '/').contains("/src/main/java/com/butchercraft/client/");
    }
}
