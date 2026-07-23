package com.butchercraft.world.transaction;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionDependencyBoundaryTest {
    @Test
    void transactionPackageRemainsIndependentOfMinecraftNeoForgeAndItemStacks() throws IOException {
        Path sourceRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/transaction");
        try (var files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = read(path);
                assertFalse(source.contains("import net.minecraft"), path + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), path + " must remain NeoForge-independent");
                assertFalse(source.contains("ItemStack"), path + " must not depend on ItemStack");
            });
        }
    }

    @Test
    void onlyTransactionExecutorUsesTheInventoryMutationAuthority() throws IOException {
        Path sourceRoot = TestProjectPaths.projectPath("src/main/java");
        long callers;
        try (var files = Files.walk(sourceRoot)) {
            callers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("applyValidatedChanges("))
                    .count();
        }
        assertEquals(2L, callers, "Only InventoryManager and TransactionExecutor may reference mutation execution");
        String executor = read(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/transaction/TransactionExecutor.java"
        ));
        assertTrue(executor.contains("applyValidatedChanges("));
        String manager = read(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/inventory/InventoryManager.java"
        ));
        assertFalse(manager.contains("public synchronized void addEntry("));
        assertFalse(manager.contains("public synchronized void removeEntry("));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Failed to read source file: " + path, exception);
        }
    }
}
