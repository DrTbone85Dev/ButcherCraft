package com.butchercraft.transformation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationDependencyBoundaryTest {
    @Test
    void transformationSourcesDoNotImportMinecraftOrNeoForge() throws IOException {
        Path transformationRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/transformation");
        assertTrue(Files.isDirectory(transformationRoot), "Transformation source directory must exist");

        List<Path> offenders;
        try (var paths = Files.walk(transformationRoot)) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(TransformationDependencyBoundaryTest::containsForbiddenDependency)
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "Transformation sources must not import Minecraft or NeoForge: " + offenders);
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
