package com.butchercraft.world.economy.order;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderContractIntegrationTest {
    @Test
    void lifecycleServiceIsRegisteredAfterItsDependencies() throws IOException {
        Path project = Path.of(System.getProperty("butchercraft.projectDir"));
        String modSource = Files.readString(project.resolve("src/main/java/com/butchercraft/ButcherCraft.java"));
        int transactionInitialize = modSource.indexOf("TransactionService.INSTANCE::initialize");
        int orderInitialize = modSource.indexOf("OrderContractService.INSTANCE::initialize");
        assertTrue(transactionInitialize >= 0 && orderInitialize > transactionInitialize);
        assertTrue(modSource.contains("OrderContractService.INSTANCE::save"));

        String service = Files.readString(project.resolve(
                "src/main/java/com/butchercraft/world/OrderContractService.java"
        ));
        assertTrue(service.contains("transactionService.managerFor(server)"));
        assertTrue(service.indexOf("contractStorage.load()") < service.indexOf("orderStorage.load()"));
        assertTrue(service.contains("activeState.set(created)"));
    }
}
