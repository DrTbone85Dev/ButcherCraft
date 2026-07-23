package com.butchercraft.world.economy.order;

import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderFulfillmentTest {
    @Test
    void appliedTransactionsSupportPartialThenExactFulfillmentWithoutFurtherInventoryMutation() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicOrderDefinition order = OrderContractTestFixtures.order("test:partial", 10);
        context.orders().submit(order);
        context.orders().accept(order.id(), 16);
        EconomicTransaction first = OrderContractTestFixtures.appliedTransaction(context, "test:first_tx", 4, 26);
        long inventoryAfterTransaction = context.inventories().quantityIn(
                com.butchercraft.world.inventory.InventoryTestFixtures.BEEF_INVENTORY,
                OrderContractTestFixtures.BEEF
        );

        assertTrue(context.orders().recordFulfillment(List.of(request(order, first, 4, 26))).success());
        assertEquals(OrderStatus.PARTIALLY_FULFILLED, context.orders().runtimeFor(order.id()).orElseThrow().status());
        assertEquals(GoodQuantity.of(4), context.orders().fulfilledQuantity(order.id(), OrderContractTestFixtures.LINE_ID));
        assertEquals(GoodQuantity.of(6), context.orders().remainingQuantity(order.id(), OrderContractTestFixtures.LINE_ID));
        assertEquals(inventoryAfterTransaction, context.inventories().quantityIn(
                com.butchercraft.world.inventory.InventoryTestFixtures.BEEF_INVENTORY,
                OrderContractTestFixtures.BEEF
        ));

        EconomicTransaction second = OrderContractTestFixtures.appliedTransaction(context, "test:second_tx", 6, 27);
        assertTrue(context.orders().recordFulfillment(List.of(request(order, second, 6, 27))).success());
        assertEquals(OrderStatus.FULFILLED, context.orders().runtimeFor(order.id()).orElseThrow().status());
        assertEquals(GoodQuantity.zero(), context.orders().remainingQuantity(order.id(), OrderContractTestFixtures.LINE_ID));
    }

    @Test
    void fulfillmentRejectsWrongGoodNonAppliedDuplicateAndExcessiveAllocations() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicOrderDefinition order = OrderContractTestFixtures.order("test:rejections", 10);
        context.orders().submit(order);
        context.orders().accept(order.id(), 16);
        EconomicTransaction transaction = OrderContractTestFixtures.appliedTransaction(context, "test:tx", 10, 26);

        assertFalse(context.orders().recordFulfillment(List.of(request(order, transaction, 11, 26))).success());
        assertEquals(GoodQuantity.zero(), context.orders().fulfilledQuantity(order.id(), OrderContractTestFixtures.LINE_ID));
        assertTrue(context.orders().recordFulfillment(List.of(request(order, transaction, 5, 26))).success());
        OrderOperationResult duplicate = context.orders().recordFulfillment(List.of(
                request(order, transaction, 1, 27)
        ));
        assertEquals(OrderFailureCode.DUPLICATE_TRANSACTION_ALLOCATION, duplicate.failureCode().orElseThrow());

        EconomicTransaction pending = EconomicTransaction.builder().id(TransactionId.of("test:pending"))
                .type(com.butchercraft.world.transaction.TransactionType.INVENTORY_ADD)
                .destinationActorId(OrderContractTestFixtures.REQUESTER)
                .destinationInventoryId(com.butchercraft.world.inventory.InventoryTestFixtures.BEEF_INVENTORY)
                .goodId(OrderContractTestFixtures.BEEF).quantity(5)
                .unitOfMeasure(com.butchercraft.world.goods.UnitOfMeasure.POUND).simulationTick(27).build();
        com.butchercraft.world.transaction.TransactionManager loadedTransactions =
                new com.butchercraft.world.transaction.TransactionManager(
                        new com.butchercraft.world.transaction.TransactionRegistry(List.of(pending)),
                        context.inventories()
                );
        ContractManager contracts = new ContractManager(context.actors());
        OrderManager pendingOrders = new OrderManager(
                context.actors(), context.inventories().registry(), loadedTransactions, contracts
        );
        EconomicOrderDefinition pendingOrder = OrderContractTestFixtures.order("test:pending_order", 5);
        pendingOrders.submit(pendingOrder);
        pendingOrders.accept(pendingOrder.id(), 16);
        OrderOperationResult pendingResult = pendingOrders.recordFulfillment(List.of(
                new OrderFulfillmentRequest(
                        pendingOrder.id(), OrderContractTestFixtures.LINE_ID, pending.id(), GoodQuantity.of(5), 27
                )
        ));
        assertEquals(OrderFailureCode.TRANSACTION_NOT_APPLIED, pendingResult.failureCode().orElseThrow());
    }

    @Test
    void multiAllocationOperationIsAtomicAcrossOrdersAndTransactionQuantity() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicOrderDefinition first = OrderContractTestFixtures.order("test:first_order", 4);
        EconomicOrderDefinition second = OrderContractTestFixtures.order("test:second_order", 4);
        context.orders().submit(first);
        context.orders().submit(second);
        context.orders().accept(first.id(), 16);
        context.orders().accept(second.id(), 16);
        EconomicTransaction transaction = OrderContractTestFixtures.appliedTransaction(context, "test:shared", 6, 26);

        OrderOperationResult rejected = context.orders().recordFulfillment(List.of(
                request(first, transaction, 4, 26), request(second, transaction, 4, 26)
        ));
        assertEquals(OrderFailureCode.TRANSACTION_QUANTITY_EXCEEDED, rejected.failureCode().orElseThrow());
        assertEquals(GoodQuantity.zero(), context.orders().fulfilledQuantity(first.id(), OrderContractTestFixtures.LINE_ID));
        assertEquals(GoodQuantity.zero(), context.orders().fulfilledQuantity(second.id(), OrderContractTestFixtures.LINE_ID));

        assertTrue(context.orders().recordFulfillment(List.of(
                request(first, transaction, 3, 26), request(second, transaction, 3, 26)
        )).success());
        assertEquals(GoodQuantity.of(3), context.orders().fulfilledQuantity(first.id(), OrderContractTestFixtures.LINE_ID));
        assertEquals(GoodQuantity.of(3), context.orders().fulfilledQuantity(second.id(), OrderContractTestFixtures.LINE_ID));
    }

    @Test
    void failedLaterAllocationDoesNotAdvanceEarlierRuntimeOrRevision() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicOrderDefinition order = EconomicOrderDefinition.builder()
                .id(OrderId.of("test:two_lines")).displayName("Two Lines").type(OrderType.REPLENISHMENT)
                .requesterActorId(OrderContractTestFixtures.REQUESTER)
                .counterpartyActorId(OrderContractTestFixtures.COUNTERPARTY)
                .lines(List.of(
                        OrderContractTestFixtures.line("test:first_line", 2, OrderLineRole.REQUESTED),
                        OrderContractTestFixtures.line("test:second_line", 2, OrderLineRole.OUTPUT)
                )).createdSimulationTick(15).build();
        context.orders().submit(order);
        context.orders().accept(order.id(), 16);
        EconomicTransaction transaction = OrderContractTestFixtures.appliedTransaction(context, "test:atomic", 3, 26);
        long revision = context.orders().runtimeFor(order.id()).orElseThrow().revision();

        OrderOperationResult result = context.orders().recordFulfillment(List.of(
                new OrderFulfillmentRequest(order.id(), OrderLineId.of("test:first_line"), transaction.id(),
                        GoodQuantity.of(2), 26),
                new OrderFulfillmentRequest(order.id(), OrderLineId.of("test:second_line"), transaction.id(),
                        GoodQuantity.of(2), 26)
        ));

        assertFalse(result.success());
        EconomicOrderRuntime unchanged = context.orders().runtimeFor(order.id()).orElseThrow();
        assertEquals(revision, unchanged.revision());
        assertTrue(unchanged.lines().stream().allMatch(line -> line.fulfilledQuantity().isZero()));
    }

    private static OrderFulfillmentRequest request(
            EconomicOrderDefinition order, EconomicTransaction transaction, long quantity, long tick
    ) {
        return new OrderFulfillmentRequest(
                order.id(), OrderContractTestFixtures.LINE_ID, transaction.id(), GoodQuantity.of(quantity), tick
        );
    }
}
