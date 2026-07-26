package com.butchercraft.workstation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationDependencyBoundaryTest {
    @Test
    void engineStillDoesNotImportMinecraftOrNeoForge() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/engine")).stream()
                .filter(path -> sourceContains(path, "import net.minecraft") || sourceContains(path, "import net.neoforged"))
                .toList();

        assertTrue(offenders.isEmpty(), "Engine must remain Minecraft-independent: " + offenders);
    }

    @Test
    void transformationModelStillDoesNotImportMinecraftOrNeoForge() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/transformation")).stream()
                .filter(path -> sourceContains(path, "import net.minecraft") || sourceContains(path, "import net.neoforged"))
                .toList();

        assertTrue(offenders.isEmpty(), "Transformation model must remain Minecraft-independent: " + offenders);
    }

    @Test
    void commonSourcesDoNotImportClientClasses() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java")).stream()
                .filter(path -> !isClientSource(path))
                .filter(path -> sourceContains(path, "import net.minecraft.client"))
                .toList();

        assertTrue(offenders.isEmpty(), "Common source must not import client classes: " + offenders);
    }

    @Test
    void genericWorkstationCodeDoesNotHardcodeBeefOrGrindBeef() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/workstation")).stream()
                .filter(path -> !path.getFileName().toString().startsWith("Development"))
                .filter(path -> sourceContains(path, "BEEF_TRIM")
                        || sourceContains(path, "GROUND_BEEF")
                        || sourceContains(path, "GRIND_BEEF")
                        || sourceContains(path, "beef_trim")
                        || sourceContains(path, "ground_beef")
                        || sourceContains(path, "grind_beef")
                        || sourceContains(path, "CHICKEN_TRIM")
                        || sourceContains(path, "GROUND_CHICKEN")
                        || sourceContains(path, "GRIND_CHICKEN")
                        || sourceContains(path, "chicken_trim")
                        || sourceContains(path, "ground_chicken")
                        || sourceContains(path, "grind_chicken")
                        || sourceContains(path, "LAMB_TRIM")
                        || sourceContains(path, "GROUND_LAMB")
                        || sourceContains(path, "GRIND_LAMB")
                        || sourceContains(path, "lamb_trim")
                        || sourceContains(path, "ground_lamb")
                        || sourceContains(path, "grind_lamb")
                        || sourceContains(path, "VENISON_TRIM")
                        || sourceContains(path, "GROUND_VENISON")
                        || sourceContains(path, "GRIND_VENISON")
                        || sourceContains(path, "venison_trim")
                        || sourceContains(path, "ground_venison")
                        || sourceContains(path, "grind_venison"))
                .toList();

        assertTrue(offenders.isEmpty(), "Generic workstation code must stay product-agnostic: " + offenders);
    }

    @Test
    void noLiteralChickenSpeciesSwitchesExist() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java")).stream()
                .filter(path -> sourceContains(path, "case \"butchercraft:chicken\"")
                        || sourceContains(path, "case \"butchercraft:poultry\"")
                        || sourceContains(path, "switch (species")
                        || sourceContains(path, "switch(species"))
                .toList();

        assertTrue(offenders.isEmpty(), "Processing differences must stay data-driven: " + offenders);
    }

    private static List<Path> javaFiles(Path root) throws IOException {
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
