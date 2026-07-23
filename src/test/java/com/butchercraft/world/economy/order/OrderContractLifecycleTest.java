package com.butchercraft.world.economy.order;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderContractLifecycleTest {
    @Test
    void everyDeclaredOrderTransitionIsAcceptedAndOthersAreRejected() {
        for (OrderStatus source : OrderStatus.values()) {
            for (OrderStatus target : OrderStatus.values()) {
                if (source == target) continue;
                EconomicOrderRuntime runtime = runtimeAt(source, target == OrderStatus.FULFILLED);
                boolean declared = source.allowedNextStatuses().contains(target);
                try {
                    runtime.transitionTo(target, 50, java.util.Optional.of("reason"));
                    assertTrue(declared, () -> source + " unexpectedly transitioned to " + target);
                } catch (IllegalStateException exception) {
                    assertFalse(declared, () -> source + " should transition to " + target);
                }
            }
        }
    }

    @Test
    void orderManagerTracksLifecycleReasonsTicksAndImmutableSnapshots() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicOrderDefinition order = OrderContractTestFixtures.order("test:lifecycle", 10);
        assertTrue(context.orders().submit(order).success());
        assertTrue(context.orders().accept(order.id(), 16).success());
        assertEquals(OrderStatus.ACCEPTED, context.orders().runtimeFor(order.id()).orElseThrow().status());
        assertFalse(context.orders().reject(order.id(), 17, "late rejection").success());
        assertFalse(context.orders().cancel(order.id(), 14, "backward").success());
        assertTrue(context.orders().cancel(order.id(), 18, "cancelled by requester").success());
        EconomicOrderRuntime closed = context.orders().runtimeFor(order.id()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, closed.status());
        assertEquals("cancelled by requester", closed.closureReason().orElseThrow());
        assertFalse(context.orders().accept(order.id(), 19).success());

        EconomicOrderRuntime detached = context.orders().runtimeFor(order.id()).orElseThrow();
        assertEquals(closed.revision(), detached.revision());
    }

    @Test
    void contractManagerSupportsExplicitLifecycleAndAssociationOnlyWhileActive() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicContractDefinition contract = OrderContractTestFixtures.contract("test:lifecycle_contract");
        assertTrue(context.contracts().register(contract).success());
        assertFalse(context.orders().submit(OrderContractTestFixtures.order(
                "test:inactive_order", 10, contract.id()
        )).success());
        assertTrue(context.contracts().activate(contract.id(), 12).success());
        EconomicOrderDefinition governed = OrderContractTestFixtures.order("test:governed", 10, contract.id());
        assertTrue(context.orders().submit(governed).success());
        assertEquals(List.of(governed.id()), context.contracts().governedOrders(contract.id()));
        assertTrue(context.contracts().suspend(contract.id(), 20, "review").success());
        assertTrue(context.contracts().resume(contract.id(), 21).success());
        assertTrue(context.contracts().complete(contract.id(), 30).success());
        assertFalse(context.contracts().activate(contract.id(), 31).success());
        assertEquals(ContractStatus.COMPLETED,
                context.contracts().runtimeFor(contract.id()).orElseThrow().status());
    }

    @Test
    void everyDeclaredContractTransitionIsAcceptedAndOthersAreRejected() {
        for (ContractStatus source : ContractStatus.values()) {
            for (ContractStatus target : ContractStatus.values()) {
                if (source == target) continue;
                EconomicContractRuntime runtime = contractRuntimeAt(source);
                boolean declared = source.allowedNextStatuses().contains(target);
                try {
                    runtime.transitionTo(target, 50, java.util.Optional.of("reason"));
                    assertTrue(declared, () -> source + " unexpectedly transitioned to " + target);
                } catch (IllegalStateException exception) {
                    assertFalse(declared, () -> source + " should transition to " + target);
                }
            }
        }
    }

    private static EconomicOrderRuntime runtimeAt(OrderStatus status, boolean targetRequiresFulfilledLine) {
        OrderLineStatus lineStatus = switch (status) {
            case FULFILLED -> OrderLineStatus.FULFILLED;
            case REJECTED, FAILED -> OrderLineStatus.FAILED;
            case CANCELLED, EXPIRED -> OrderLineStatus.CANCELLED;
            default -> OrderLineStatus.OPEN;
        };
        if (targetRequiresFulfilledLine) lineStatus = OrderLineStatus.FULFILLED;
        OrderLineRuntime line = lineStatus == OrderLineStatus.FULFILLED
                ? new OrderLineRuntime(
                        OrderContractTestFixtures.LINE_ID, GoodQuantity.of(1), lineStatus,
                        List.of(new OrderFulfillmentAllocation(
                                com.butchercraft.world.transaction.TransactionId.of("test:tx"),
                                GoodQuantity.of(1), 20
                        )), java.util.OptionalLong.of(20), OrderContractSchema.CURRENT_VERSION
                )
                : new OrderLineRuntime(
                        OrderContractTestFixtures.LINE_ID, GoodQuantity.zero(), lineStatus, List.of(),
                        java.util.OptionalLong.empty(), OrderContractSchema.CURRENT_VERSION
                );
        return new EconomicOrderRuntime(
                OrderContractTestFixtures.ORDER_ID, status, 20, List.of(line),
                status == OrderStatus.ACCEPTED || status == OrderStatus.PARTIALLY_FULFILLED
                        || status == OrderStatus.FULFILLED
                        ? java.util.OptionalLong.of(15) : java.util.OptionalLong.empty(),
                status.isTerminal() ? java.util.OptionalLong.of(20) : java.util.OptionalLong.empty(),
                java.util.Optional.empty(), 1, OrderContractSchema.CURRENT_VERSION
        );
    }

    private static EconomicContractRuntime contractRuntimeAt(ContractStatus status) {
        return new EconomicContractRuntime(
                OrderContractTestFixtures.CONTRACT_ID, status, 20,
                status == ContractStatus.ACTIVE || status == ContractStatus.SUSPENDED
                        || status == ContractStatus.COMPLETED
                        ? java.util.OptionalLong.of(12) : java.util.OptionalLong.empty(),
                status.isTerminal() ? java.util.OptionalLong.of(20) : java.util.OptionalLong.empty(),
                List.of(), java.util.Optional.empty(), java.util.Optional.empty(), 1,
                OrderContractSchema.CURRENT_VERSION
        );
    }
}
