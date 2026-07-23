package com.butchercraft.architecture;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureValidationDependencyBoundaryTest {
    @Test
    void frameworkContainsNoMinecraftNeoForgeReflectionScanningRandomnessOrWallClockDependencies()
            throws IOException {
        Path root = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/architecture/validation"
        );
        List<String> forbidden = List.of(
                "net.minecraft",
                "net.neoforged",
                "java.lang.reflect",
                "Class.forName",
                "getDeclared",
                "Files.walk",
                "System.currentTimeMillis",
                "System.nanoTime",
                "java.util.Random",
                "ThreadLocalRandom"
        );

        try (var files = Files.walk(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Architecture validation boundary violations: " + violations);
        }
    }

    @Test
    void manifestAdapterRemainsFreeOfMinecraftAndNeoForgeImports() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/architecture");
        try (var files = Files.walk(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, List.of("net.minecraft", "net.neoforged")))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Architecture manifest boundary violations: " + violations);
        }
    }

    private static boolean containsAny(Path path, List<String> forbidden) {
        try {
            String content = Files.readString(path);
            return forbidden.stream().anyMatch(content::contains);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
