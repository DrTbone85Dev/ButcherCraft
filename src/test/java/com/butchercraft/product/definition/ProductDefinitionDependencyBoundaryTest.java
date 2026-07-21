package com.butchercraft.product.definition;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDefinitionDependencyBoundaryTest {
    @Test
    void productDefinitionSourcesDoNotImportMinecraftOrNeoForge() throws IOException {
        List<Path> offenders;
        try (var paths = javaFiles(
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/product/definition"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/product/datapack"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/product/serialization")
        )) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ProductDefinitionDependencyBoundaryTest::containsForbiddenDependency)
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "Product definition sources must not import Minecraft or NeoForge: " + offenders);
    }

    private static java.util.stream.Stream<Path> javaFiles(Path... roots) throws IOException {
        java.util.List<Path> files = new java.util.ArrayList<>();
        for (Path root : roots) {
            assertTrue(Files.isDirectory(root), "Product source directory must exist: " + root);
            try (var paths = Files.walk(root)) {
                files.addAll(paths.filter(path -> path.toString().endsWith(".java")).toList());
            }
        }
        return files.stream();
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
