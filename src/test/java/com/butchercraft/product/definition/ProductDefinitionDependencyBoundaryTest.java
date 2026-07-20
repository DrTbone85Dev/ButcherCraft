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
        Path productDefinitionRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/product/definition");
        assertTrue(Files.isDirectory(productDefinitionRoot), "Product definition source directory must exist");

        List<Path> offenders;
        try (var paths = Files.walk(productDefinitionRoot)) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ProductDefinitionDependencyBoundaryTest::containsForbiddenDependency)
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "Product definition sources must not import Minecraft or NeoForge: " + offenders);
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
