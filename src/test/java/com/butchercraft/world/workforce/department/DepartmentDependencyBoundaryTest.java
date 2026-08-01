package com.butchercraft.world.workforce.department;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DepartmentDependencyBoundaryTest {
    @Test
    void departmentDomainPackageDoesNotImportMinecraftOrNeoForgeApis() throws IOException {
        Path departmentPackage = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/workforce/department"
        );
        try (var files = Files.walk(departmentPackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
            }
        }
    }
}
