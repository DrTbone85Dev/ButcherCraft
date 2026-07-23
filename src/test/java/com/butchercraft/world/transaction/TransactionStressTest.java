package com.butchercraft.world.transaction;

import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStressTest {
    private static final int TRANSACTION_COUNT = 1_000_000;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void registryStoresAndFindsOneMillionTransactionsInDeterministicOrder() {
        TransactionRegistry registry = new TransactionRegistry();
        for (int index = 0; index < TRANSACTION_COUNT; index++) {
            registry.register(EconomicTransaction.builder()
                    .id(TransactionId.of("stress:transaction_" + index))
                    .type(TransactionType.SYSTEM)
                    .goodId(InventoryTestFixtures.BEEF)
                    .quantity(1L)
                    .unitOfMeasure(UnitOfMeasure.POUND)
                    .simulationTick(index)
                    .build());
        }

        assertEquals(TRANSACTION_COUNT, registry.size());
        assertTrue(registry.contains(TransactionId.of("stress:transaction_0")));
        assertTrue(registry.contains(TransactionId.of("stress:transaction_500000")));
        assertTrue(registry.contains(TransactionId.of("stress:transaction_999999")));
        List<EconomicTransaction> history = registry.history();
        assertEquals("stress:transaction_0", history.getFirst().id().value());
        assertEquals("stress:transaction_999999", history.getLast().id().value());
        assertEquals(TRANSACTION_COUNT, history.stream().map(EconomicTransaction::id).distinct().count());
    }
}
