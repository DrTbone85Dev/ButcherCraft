package com.butchercraft.world.transaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionRegistryTest {
    @Test
    void registryPreservesAuthoritativeHistoryOrderAndSupportsLookup() {
        EconomicTransaction second = TransactionTestFixtures.beefTransaction(
                "test:second", TransactionType.INVENTORY_ADD, 1L, 2L
        );
        EconomicTransaction first = TransactionTestFixtures.beefTransaction(
                "test:first", TransactionType.INVENTORY_REMOVE, 1L, 1L
        );
        TransactionRegistry registry = new TransactionRegistry(List.of(second, first));

        assertEquals(List.of(second, first), registry.history());
        assertEquals(2, registry.size());
        assertTrue(registry.contains(first.id()));
        assertEquals(first, registry.find(first.id()).orElseThrow());
        assertEquals(List.of(second), registry.findByType(TransactionType.INVENTORY_ADD));
        assertEquals(2L, registry.stream().count());
    }

    @Test
    void registryRejectsDuplicatesAndPreservesPositionDuringStatusReplacement() {
        EconomicTransaction first = TransactionTestFixtures.beefTransaction(
                "test:first", TransactionType.INVENTORY_ADD, 1L, 1L
        );
        EconomicTransaction second = TransactionTestFixtures.beefTransaction(
                "test:second", TransactionType.INVENTORY_ADD, 1L, 2L
        );
        TransactionRegistry registry = new TransactionRegistry(List.of(first, second));

        assertThrows(IllegalArgumentException.class, () -> registry.register(first));
        registry.replace(first.withStatus(TransactionStatus.APPLIED));

        assertEquals(first.id(), registry.history().getFirst().id());
        assertEquals(TransactionStatus.APPLIED, registry.history().getFirst().status());
        assertThrows(IllegalArgumentException.class, () -> registry.replace(
                TransactionTestFixtures.beefTransaction("test:missing", TransactionType.INVENTORY_ADD, 1L, 3L)
        ));
    }
}
