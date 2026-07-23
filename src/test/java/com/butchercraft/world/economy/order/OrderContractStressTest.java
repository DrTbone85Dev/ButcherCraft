package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.transaction.TransactionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderContractStressTest {
    private static final int ORDER_COUNT = 100_000;
    private static final int ORDER_LINES_PER_DEFINITION = 10;
    private static final int CONTRACT_COUNT = 25_000;
    private static final int CONTRACT_LINES_PER_DEFINITION = 10;
    private static final int ALLOCATION_COUNT = 1_000_000;

    @Test
    void registersOneHundredThousandOrdersAndOneMillionLineReferencesDeterministically() {
        List<OrderLineDefinition> lines = orderLines();
        OrderRegistryBuilder builder = OrderRegistry.builder();
        long started = System.nanoTime();
        for (int index = 0; index < ORDER_COUNT; index++) {
            builder.register(EconomicOrderDefinition.builder()
                    .id(OrderId.of("stress:order_" + index)).displayName("Stress Order " + index)
                    .type(OrderType.SYSTEM).requesterActorId(ActorId.of("stress:system"))
                    .lines(lines).createdSimulationTick(index).build());
        }
        OrderRegistry registry = builder.build();
        long elapsed = System.nanoTime() - started;

        assertEquals(ORDER_COUNT, registry.size());
        assertEquals(ORDER_COUNT * ORDER_LINES_PER_DEFINITION,
                registry.definitions().stream().mapToInt(definition -> definition.lines().size()).sum());
        assertEquals(ORDER_COUNT, registry.findByGood(GoodId.of("stress:good_0")).size());
        assertEquals(OrderId.of("stress:order_0"), registry.definitions().getFirst().id());
        assertEquals(OrderId.of("stress:order_99999"), registry.definitions().getLast().id());
        System.out.printf("Order stress: %,d orders / %,d lines in %.3f s%n",
                ORDER_COUNT, ORDER_COUNT * ORDER_LINES_PER_DEFINITION, elapsed / 1_000_000_000.0);
    }

    @Test
    void registersTwentyFiveThousandContractsAndQuarterMillionLineReferencesDeterministically() {
        List<ContractLineDefinition> lines = contractLines();
        ContractRegistryBuilder builder = ContractRegistry.builder();
        long started = System.nanoTime();
        for (int index = 0; index < CONTRACT_COUNT; index++) {
            builder.register(EconomicContractDefinition.builder()
                    .id(ContractId.of("stress:contract_" + index)).displayName("Stress Contract " + index)
                    .type(ContractType.FRAMEWORK).principalActorId(ActorId.of("stress:principal"))
                    .counterpartyActorId(ActorId.of("stress:counterparty"))
                    .lines(lines).effectiveSimulationTick(index).build());
        }
        ContractRegistry registry = builder.build();
        long elapsed = System.nanoTime() - started;

        assertEquals(CONTRACT_COUNT, registry.size());
        assertEquals(CONTRACT_COUNT * CONTRACT_LINES_PER_DEFINITION,
                registry.definitions().stream().mapToInt(definition -> definition.lines().size()).sum());
        assertEquals(CONTRACT_COUNT, registry.findByGood(GoodId.of("stress:good_0")).size());
        assertEquals(ContractId.of("stress:contract_0"), registry.definitions().getFirst().id());
        assertEquals(ContractId.of("stress:contract_24999"), registry.definitions().getLast().id());
        System.out.printf("Contract stress: %,d contracts / %,d lines in %.3f s%n",
                CONTRACT_COUNT, CONTRACT_COUNT * CONTRACT_LINES_PER_DEFINITION,
                elapsed / 1_000_000_000.0);
    }

    @Test
    void validatesOneMillionOrderedUniqueFulfillmentAllocations() {
        List<OrderFulfillmentAllocation> allocations = new ArrayList<>(ALLOCATION_COUNT);
        GoodQuantity one = GoodQuantity.of(1);
        for (int index = 0; index < ALLOCATION_COUNT; index++) {
            allocations.add(new OrderFulfillmentAllocation(
                    TransactionId.of("stress:transaction_" + index), one, index
            ));
        }
        long started = System.nanoTime();
        OrderLineRuntime runtime = new OrderLineRuntime(
                OrderLineId.of("stress:line"), GoodQuantity.of(ALLOCATION_COUNT),
                OrderLineStatus.FULFILLED, allocations, java.util.OptionalLong.of(ALLOCATION_COUNT - 1L),
                OrderContractSchema.CURRENT_VERSION
        );
        long elapsed = System.nanoTime() - started;

        assertEquals(ALLOCATION_COUNT, runtime.allocations().size());
        assertEquals(GoodQuantity.of(ALLOCATION_COUNT), runtime.fulfilledQuantity());
        assertEquals(TransactionId.of("stress:transaction_0"), runtime.allocations().getFirst().transactionId());
        assertEquals(TransactionId.of("stress:transaction_999999"), runtime.allocations().getLast().transactionId());
        System.out.printf("Allocation stress: %,d allocations validated in %.3f s%n",
                ALLOCATION_COUNT, elapsed / 1_000_000_000.0);
    }

    private static List<OrderLineDefinition> orderLines() {
        List<OrderLineDefinition> lines = new ArrayList<>(ORDER_LINES_PER_DEFINITION);
        for (int index = 0; index < ORDER_LINES_PER_DEFINITION; index++) {
            lines.add(OrderLineDefinition.builder().id(OrderLineId.of("stress:line_" + index))
                    .goodId(GoodId.of("stress:good_" + index)).requestedQuantity(GoodQuantity.of(1))
                    .unitOfMeasure(UnitOfMeasure.EACH).role(OrderLineRole.REQUESTED).build());
        }
        return List.copyOf(lines);
    }

    private static List<ContractLineDefinition> contractLines() {
        List<ContractLineDefinition> lines = new ArrayList<>(CONTRACT_LINES_PER_DEFINITION);
        for (int index = 0; index < CONTRACT_LINES_PER_DEFINITION; index++) {
            lines.add(ContractLineDefinition.builder().id(ContractLineId.of("stress:line_" + index))
                    .goodId(GoodId.of("stress:good_" + index)).committedQuantity(GoodQuantity.of(1))
                    .unitOfMeasure(UnitOfMeasure.EACH).build());
        }
        return List.copyOf(lines);
    }
}
