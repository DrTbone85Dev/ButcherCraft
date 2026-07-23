package com.butchercraft.world.production;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionDependencyBoundaryTest {
    @Test
    void productionDomainContainsNoMinecraftNeoForgeItemStackOrWallClockImports() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/production");
        List<String> forbidden = List.of(
                "net.minecraft",
                "net.neoforged",
                "ItemStack",
                "java.time",
                "System.currentTimeMillis",
                "System.nanoTime"
        );
        try (var files = Files.walk(root)) {
            List<Path> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Production dependency boundary violations: " + violations);
        }
    }

    private static boolean containsAny(Path file, List<String> forbidden) {
        try {
            String content = Files.readString(file);
            return forbidden.stream().anyMatch(content::contains);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
