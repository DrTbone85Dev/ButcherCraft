package com.butchercraft.world.economy.order;

import com.butchercraft.world.transaction.TransactionId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public final class OrderLineRuntime {
    private final OrderLineId orderLineId;
    private GoodQuantity fulfilledQuantity;
    private OrderLineStatus status;
    private final List<OrderFulfillmentAllocation> allocations;
    private OptionalLong lastFulfillmentTick;
    private final int schemaVersion;

    public OrderLineRuntime(
            OrderLineId orderLineId,
            GoodQuantity fulfilledQuantity,
            OrderLineStatus status,
            Collection<OrderFulfillmentAllocation> allocations,
            OptionalLong lastFulfillmentTick,
            int schemaVersion
    ) {
        this.orderLineId = Objects.requireNonNull(orderLineId, "orderLineId");
        this.fulfilledQuantity = Objects.requireNonNull(fulfilledQuantity, "fulfilledQuantity");
        this.status = Objects.requireNonNull(status, "status");
        this.allocations = new ArrayList<>(copyAllocations(allocations));
        this.lastFulfillmentTick = Objects.requireNonNull(lastFulfillmentTick, "lastFulfillmentTick");
        this.schemaVersion = DomainValidation.requireSchema(schemaVersion, "order line runtime");
        validateAllocationTotals();
    }

    public static OrderLineRuntime open(OrderLineId lineId) {
        return new OrderLineRuntime(
                lineId, GoodQuantity.zero(), OrderLineStatus.OPEN, List.of(), OptionalLong.empty(),
                OrderContractSchema.CURRENT_VERSION
        );
    }

    public synchronized OrderLineId orderLineId() { return orderLineId; }
    public synchronized GoodQuantity fulfilledQuantity() { return fulfilledQuantity; }
    public synchronized OrderLineStatus status() { return status; }
    public synchronized List<OrderFulfillmentAllocation> allocations() { return List.copyOf(allocations); }
    public synchronized OptionalLong lastFulfillmentTick() { return lastFulfillmentTick; }
    public int schemaVersion() { return schemaVersion; }

    public synchronized GoodQuantity remainingQuantity(GoodQuantity requestedQuantity) {
        Objects.requireNonNull(requestedQuantity, "requestedQuantity");
        return fulfilledQuantity.compareTo(requestedQuantity) >= 0
                ? GoodQuantity.zero()
                : requestedQuantity.subtract(fulfilledQuantity);
    }

    public synchronized List<TransactionId> transactionIds() {
        return allocations.stream().map(OrderFulfillmentAllocation::transactionId).distinct().toList();
    }

    public synchronized OrderLineRuntime snapshot() {
        return new OrderLineRuntime(
                orderLineId, fulfilledQuantity, status, allocations, lastFulfillmentTick, schemaVersion
        );
    }

    synchronized void applyAllocation(
            OrderFulfillmentAllocation allocation,
            GoodQuantity requestedQuantity,
            boolean overFulfillmentAllowed
    ) {
        Objects.requireNonNull(allocation, "allocation");
        if (status.isTerminal()) {
            throw new IllegalStateException("Terminal order line cannot receive fulfillment: " + orderLineId.value());
        }
        if (lastFulfillmentTick.isPresent() && allocation.simulationTick() < lastFulfillmentTick.orElseThrow()) {
            throw new IllegalStateException("Order line fulfillment tick cannot move backward");
        }
        if (allocations.stream().anyMatch(existing -> existing.transactionId().equals(allocation.transactionId()))) {
            throw new IllegalStateException("Duplicate transaction allocation on order line");
        }
        GoodQuantity next = fulfilledQuantity.add(allocation.quantity());
        if (!overFulfillmentAllowed && next.compareTo(requestedQuantity) > 0) {
            throw new IllegalStateException("Order line fulfillment exceeds requested quantity");
        }
        allocations.add(allocation);
        fulfilledQuantity = next;
        lastFulfillmentTick = OptionalLong.of(allocation.simulationTick());
        status = next.compareTo(requestedQuantity) >= 0
                ? OrderLineStatus.FULFILLED
                : OrderLineStatus.PARTIALLY_FULFILLED;
    }

    synchronized void close(OrderLineStatus terminalStatus) {
        if (terminalStatus != OrderLineStatus.CANCELLED && terminalStatus != OrderLineStatus.FAILED) {
            throw new IllegalArgumentException("Order line close status must be cancelled or failed");
        }
        if (!status.isTerminal()) {
            status = terminalStatus;
        }
    }

    private void validateAllocationTotals() {
        GoodQuantity total = GoodQuantity.zero();
        Set<TransactionId> transactionIds = new HashSet<>();
        long previousTick = -1L;
        for (OrderFulfillmentAllocation allocation : allocations) {
            if (!transactionIds.add(allocation.transactionId())) {
                throw new IllegalArgumentException("Duplicate transaction allocation on order line");
            }
            if (allocation.simulationTick() < previousTick) {
                throw new IllegalArgumentException("Order line allocation ticks must be monotonic");
            }
            previousTick = allocation.simulationTick();
            total = total.add(allocation.quantity());
        }
        if (!total.equals(fulfilledQuantity)) {
            throw new IllegalArgumentException("Order line fulfilled quantity does not equal allocation total");
        }
        if (allocations.isEmpty() != lastFulfillmentTick.isEmpty()) {
            throw new IllegalArgumentException("Order line last fulfillment tick is inconsistent with allocations");
        }
        if (!allocations.isEmpty() && lastFulfillmentTick.orElseThrow() != previousTick) {
            throw new IllegalArgumentException("Order line last fulfillment tick does not match allocations");
        }
    }

    private static List<OrderFulfillmentAllocation> copyAllocations(Collection<OrderFulfillmentAllocation> source) {
        return Objects.requireNonNull(source, "allocations").stream()
                .map(allocation -> Objects.requireNonNull(allocation, "allocation"))
                .toList();
    }
}
