package com.butchercraft.world.identity;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentityDependencyBoundaryTest {
    @Test
    void worldIdentityDomainDoesNotImportMinecraftOrNeoForge() throws IOException {
        Path domainRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/identity");
        assertTrue(Files.isDirectory(domainRoot), "World identity domain source directory must exist");

        List<Path> offenders;
        try (var paths = Files.walk(domainRoot)) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(WorldIdentityDependencyBoundaryTest::containsForbiddenDependency)
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "World identity domain sources must not import Minecraft or NeoForge: " + offenders);
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
