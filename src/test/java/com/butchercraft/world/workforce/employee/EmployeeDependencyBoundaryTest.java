package com.butchercraft.world.workforce.employee;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EmployeeDependencyBoundaryTest {
    @Test
    void employeeDomainPackageDoesNotImportMinecraftOrNeoForgeApis() throws IOException {
        Path employeePackage = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/workforce/employee"
        );
        try (var files = Files.walk(employeePackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
            }
        }
    }
}
