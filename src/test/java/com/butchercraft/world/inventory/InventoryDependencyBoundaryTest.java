package com.butchercraft.world.inventory;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class InventoryDependencyBoundaryTest {
    @Test
    void inventoryPackageDoesNotImportMinecraftNeoForgeOrItemStackApis() throws IOException {
        Path inventoryPackage = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/inventory");
        try (var files = Files.walk(inventoryPackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
                assertFalse(source.contains("ItemStack"), file + " must not depend on ItemStack");
                assertFalse(
                        source.contains("net.minecraft.world.Container"),
                        file + " must not depend on Minecraft Container APIs"
                );
            }
        }
    }
}
