package com.butchercraft.world.economy.order;

import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderContractRegistryTest {
    @Test
    void orderRegistryPreservesRegistrationOrderAndIndexesCommonQueries() {
        EconomicOrderDefinition second = OrderContractTestFixtures.order("test:second", 20);
        EconomicOrderDefinition first = OrderContractTestFixtures.order("test:first", 10);
        OrderRegistry registry = OrderRegistry.builder().register(second).register(first).build();

        assertEquals(List.of(second, first), registry.definitions());
        assertEquals(List.of(second, first), registry.findByRequester(OrderContractTestFixtures.REQUESTER));
        assertEquals(List.of(second, first), registry.findByParty(OrderContractTestFixtures.COUNTERPARTY));
        assertEquals(List.of(second, first), registry.findByGood(OrderContractTestFixtures.BEEF));
        assertEquals(List.of(second, first), registry.findByType(OrderType.REPLENISHMENT));
        assertEquals(List.of(second, first), registry.findCreatedBetween(15, 15));
        assertEquals(List.of(second, first), registry.findRequestedBetween(30, 30));
        assertThrows(UnsupportedOperationException.class, () -> registry.definitions().clear());
        assertThrows(IllegalArgumentException.class, () -> OrderRegistry.builder()
                .register(first).register(first));
    }

    @Test
    void contractRegistryPreservesRegistrationOrderAndIndexesScope() {
        EconomicContractDefinition second = OrderContractTestFixtures.contract("test:second_contract");
        EconomicContractDefinition first = OrderContractTestFixtures.contract("test:first_contract");
        ContractRegistry registry = ContractRegistry.builder().register(second).register(first).build();

        assertEquals(List.of(second, first), registry.definitions());
        assertEquals(List.of(second, first), registry.findByPrincipal(OrderContractTestFixtures.REQUESTER));
        assertEquals(List.of(second, first), registry.findByParty(OrderContractTestFixtures.COUNTERPARTY));
        assertEquals(List.of(second, first), registry.findByGood(OrderContractTestFixtures.BEEF));
        assertEquals(List.of(second, first), registry.findByType(ContractType.SUPPLY));
        assertEquals(List.of(second, first), registry.findByIndustry(BuiltInIndustryCatalog.AGRICULTURE));
        assertEquals(List.of(second, first), registry.activeAt(50));
        assertEquals(List.of(second, first), registry.expiringBetween(90, 100));
        assertTrue(registry.find(ContractId.of("test:missing")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ContractRegistry.builder()
                .register(first).register(first));
    }
}
