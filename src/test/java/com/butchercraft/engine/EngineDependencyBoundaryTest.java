package com.butchercraft.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineDependencyBoundaryTest {
    @Test
    void engineSourcesDoNotImportMinecraftOrNeoForge() throws IOException {
        Path engineRoot = Path.of("src/main/java/com/butchercraft/engine");
        assertTrue(Files.isDirectory(engineRoot), "Engine source directory must exist");

        List<Path> offenders;
        try (var paths = Files.walk(engineRoot)) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(EngineDependencyBoundaryTest::containsForbiddenDependency)
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "Engine sources must not import Minecraft or NeoForge: " + offenders);
    }

    private static boolean containsForbiddenDependency(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("import net.minecraft") || source.contains("import net.neoforged");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan " + path, exception);
        }
    }
}
