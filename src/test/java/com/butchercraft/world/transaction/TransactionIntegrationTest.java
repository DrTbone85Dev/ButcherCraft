package com.butchercraft.world.transaction;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionIntegrationTest {
    @Test
    void modRegistersTransactionLifecycleAfterInventoryLifecycle() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"
        ));
        int inventoryInitialize = source.indexOf("InventoryService.INSTANCE::initialize");
        int transactionInitialize = source.indexOf("TransactionService.INSTANCE::initialize");
        int inventorySave = source.indexOf("InventoryService.INSTANCE::save");
        int transactionSave = source.indexOf("TransactionService.INSTANCE::save");

        assertTrue(inventoryInitialize >= 0);
        assertTrue(transactionInitialize > inventoryInitialize);
        assertTrue(inventorySave >= 0);
        assertTrue(transactionSave > inventorySave);
    }
}
