package com.butchercraft.world.economy.order;

import com.butchercraft.world.goods.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderContractDefinitionTest {
    @Test
    void identifiersAndExactQuantitiesAreCanonicalAndValidated() {
        assertEquals("12.34", GoodQuantity.of("12.3400").canonicalValue());
        assertEquals(GoodQuantity.of("3"), new GoodQuantity(new BigDecimal("3.000")));
        assertThrows(IllegalArgumentException.class, () -> GoodQuantity.of("-1"));
        assertThrows(IllegalArgumentException.class, () -> GoodQuantity.of("0.0000000001"));
        assertThrows(IllegalArgumentException.class, () -> OrderId.of("Not Canonical"));
        assertTrue(OrderId.of("test:a").compareTo(OrderId.of("test:b")) < 0);
    }

    @Test
    void orderDefinitionsAreImmutableDeterministicAndRejectInvalidStructure() {
        OrderLineDefinition first = OrderContractTestFixtures.line("test:z", 10, OrderLineRole.REQUESTED);
        OrderLineDefinition second = OrderContractTestFixtures.line("test:a", 5, OrderLineRole.OUTPUT);
        EconomicOrderDefinition definition = EconomicOrderDefinition.builder()
                .id(OrderId.of("test:immutable")).displayName("Immutable").type(OrderType.INTERNAL)
                .requesterActorId(OrderContractTestFixtures.REQUESTER)
                .lines(List.of(first, second)).createdSimulationTick(2).tags(Set.of(OrderTag.of("test:b")))
                .build();

        assertEquals(OrderLineId.of("test:a"), definition.lines().getFirst().id());
        assertThrows(UnsupportedOperationException.class, () -> definition.lines().add(first));
        assertThrows(UnsupportedOperationException.class, () -> definition.tags().clear());
        assertThrows(IllegalArgumentException.class, () -> EconomicOrderDefinition.builder()
                .id(OrderId.of("test:empty")).displayName("Empty").type(OrderType.INTERNAL)
                .requesterActorId(OrderContractTestFixtures.REQUESTER).createdSimulationTick(0).build());
        assertThrows(IllegalArgumentException.class, () -> EconomicOrderDefinition.builder()
                .id(OrderId.of("test:duplicate")).displayName("Duplicate").type(OrderType.INTERNAL)
                .requesterActorId(OrderContractTestFixtures.REQUESTER).createdSimulationTick(0)
                .lines(List.of(first, first)).build());
        assertThrows(IllegalArgumentException.class, () -> EconomicOrderDefinition.builder()
                .id(OrderId.of("test:ticks")).displayName("Ticks").type(OrderType.INTERNAL)
                .requesterActorId(OrderContractTestFixtures.REQUESTER).createdSimulationTick(10)
                .requestedFulfillmentTick(9).lines(List.of(first)).build());
    }

    @Test
    void contractDefinitionsValidateSchedulesTermsAndQuantityBounds() {
        EconomicContractDefinition contract = OrderContractTestFixtures.contract("test:valid_contract");
        assertEquals(ContractScheduleType.INTERVAL, contract.schedule().type());
        assertEquals(UnitOfMeasure.POUND, contract.lines().getFirst().unitOfMeasure());
        assertThrows(UnsupportedOperationException.class, () -> contract.lines().clear());
        assertThrows(IllegalArgumentException.class, () -> ContractSchedule.interval(0));
        assertThrows(IllegalArgumentException.class, () -> new ContractSchedule(
                ContractScheduleType.SEASONAL, java.util.OptionalLong.empty(), java.util.Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new ContractTerms(
                true, false, false, java.util.OptionalLong.of(2), false,
                java.util.OptionalInt.empty(), java.util.Optional.empty(), SubstitutionPolicy.EXACT_ONLY
        ));
        assertThrows(IllegalArgumentException.class, () -> ContractLineDefinition.builder()
                .id(ContractLineId.of("test:bad")).goodId(OrderContractTestFixtures.BEEF)
                .committedQuantity(GoodQuantity.of(10)).minimumQuantity(GoodQuantity.of(11))
                .unitOfMeasure(UnitOfMeasure.POUND).build());
    }
}
