package com.butchercraft.world.economy.order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class EconomicOrderRuntime {
    private final OrderId orderId;
    private OrderStatus status;
    private long lastUpdatedSimulationTick;
    private final Map<OrderLineId, OrderLineRuntime> lines;
    private OptionalLong acceptedSimulationTick;
    private OptionalLong closedSimulationTick;
    private Optional<String> closureReason;
    private long revision;
    private final int schemaVersion;

    public EconomicOrderRuntime(
            OrderId orderId,
            OrderStatus status,
            long lastUpdatedSimulationTick,
            Collection<OrderLineRuntime> lines,
            OptionalLong acceptedSimulationTick,
            OptionalLong closedSimulationTick,
            Optional<String> closureReason,
            long revision,
            int schemaVersion
    ) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = Objects.requireNonNull(status, "status");
        this.lastUpdatedSimulationTick = DomainValidation.requireTick(
                lastUpdatedSimulationTick, "Order runtime update tick"
        );
        this.lines = copyLines(lines);
        this.acceptedSimulationTick = Objects.requireNonNull(acceptedSimulationTick, "acceptedSimulationTick");
        this.closedSimulationTick = Objects.requireNonNull(closedSimulationTick, "closedSimulationTick");
        this.closureReason = DomainValidation.optionalText(closureReason, "Order closure reason", 512);
        if (revision < 0L) {
            throw new IllegalArgumentException("Order runtime revision must not be negative");
        }
        this.revision = revision;
        this.schemaVersion = DomainValidation.requireSchema(schemaVersion, "order runtime");
        validateState();
    }

    public static EconomicOrderRuntime submitted(EconomicOrderDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new EconomicOrderRuntime(
                definition.id(), OrderStatus.SUBMITTED, definition.createdSimulationTick(),
                definition.lines().stream().map(line -> OrderLineRuntime.open(line.id())).toList(),
                OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), 0L,
                OrderContractSchema.CURRENT_VERSION
        );
    }

    public synchronized OrderId orderId() { return orderId; }
    public synchronized OrderStatus status() { return status; }
    public synchronized long lastUpdatedSimulationTick() { return lastUpdatedSimulationTick; }
    public synchronized OptionalLong acceptedSimulationTick() { return acceptedSimulationTick; }
    public synchronized OptionalLong closedSimulationTick() { return closedSimulationTick; }
    public synchronized Optional<String> closureReason() { return closureReason; }
    public synchronized long revision() { return revision; }
    public int schemaVersion() { return schemaVersion; }

    public synchronized List<com.butchercraft.world.transaction.TransactionId> transactionIds() {
        return lines.values().stream().flatMap(line -> line.transactionIds().stream()).distinct().toList();
    }

    public synchronized List<OrderLineRuntime> lines() {
        return lines.values().stream().map(OrderLineRuntime::snapshot).toList();
    }

    public synchronized Optional<OrderLineRuntime> findLine(OrderLineId lineId) {
        OrderLineRuntime line = lines.get(Objects.requireNonNull(lineId, "lineId"));
        return line == null ? Optional.empty() : Optional.of(line.snapshot());
    }

    public synchronized EconomicOrderRuntime snapshot() {
        return new EconomicOrderRuntime(
                orderId, status, lastUpdatedSimulationTick, lines.values(), acceptedSimulationTick,
                closedSimulationTick, closureReason, revision, schemaVersion
        );
    }

    synchronized OrderLineRuntime mutableLine(OrderLineId lineId) {
        return lines.get(Objects.requireNonNull(lineId, "lineId"));
    }

    synchronized void transitionTo(OrderStatus nextStatus, long simulationTick, Optional<String> reason) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        requireForwardTick(simulationTick);
        if (!status.allowedNextStatuses().contains(nextStatus)) {
            throw new IllegalStateException("Invalid order status transition: " + status + " -> " + nextStatus);
        }
        if (nextStatus == OrderStatus.FULFILLED
                && lines.values().stream().anyMatch(line -> line.status() != OrderLineStatus.FULFILLED)) {
            throw new IllegalStateException("Fulfilled order requires every line to be fulfilled");
        }
        status = nextStatus;
        lastUpdatedSimulationTick = simulationTick;
        revision++;
        if (nextStatus == OrderStatus.ACCEPTED) {
            acceptedSimulationTick = OptionalLong.of(simulationTick);
        }
        if (nextStatus.isTerminal()) {
            closedSimulationTick = OptionalLong.of(simulationTick);
            closureReason = DomainValidation.optionalText(reason, "Order closure reason", 512);
            if (nextStatus == OrderStatus.CANCELLED || nextStatus == OrderStatus.EXPIRED) {
                lines.values().forEach(line -> line.close(OrderLineStatus.CANCELLED));
            } else if (nextStatus == OrderStatus.FAILED || nextStatus == OrderStatus.REJECTED) {
                lines.values().forEach(line -> line.close(OrderLineStatus.FAILED));
            }
        }
    }

    synchronized void recordFulfillmentApplied(long simulationTick, boolean allFulfilled) {
        requireForwardTick(simulationTick);
        status = allFulfilled ? OrderStatus.FULFILLED : OrderStatus.PARTIALLY_FULFILLED;
        lastUpdatedSimulationTick = simulationTick;
        revision++;
        if (allFulfilled) {
            closedSimulationTick = OptionalLong.of(simulationTick);
            closureReason = Optional.empty();
        }
    }

    public synchronized void validateAgainst(EconomicOrderDefinition definition) {
        validateAgainst(definition, false);
    }

    public synchronized void validateAgainst(EconomicOrderDefinition definition, boolean overFulfillmentAllowed) {
        Objects.requireNonNull(definition, "definition");
        if (!orderId.equals(definition.id())) {
            throw new IllegalArgumentException("Order runtime definition id mismatch");
        }
        if (lastUpdatedSimulationTick < definition.createdSimulationTick()) {
            throw new IllegalArgumentException("Order runtime update tick precedes creation");
        }
        if (!lines.keySet().equals(definition.lines().stream()
                .map(OrderLineDefinition::id).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("Order runtime line identities do not match definition");
        }
        for (OrderLineDefinition line : definition.lines()) {
            OrderLineRuntime runtime = lines.get(line.id());
            if (!overFulfillmentAllowed && runtime.fulfilledQuantity().compareTo(line.requestedQuantity()) > 0) {
                throw new IllegalArgumentException("Persisted order line is over-fulfilled: " + line.id().value());
            }
            GoodQuantity remaining = runtime.remainingQuantity(line.requestedQuantity());
            if (runtime.status() == OrderLineStatus.FULFILLED && !remaining.isZero()) {
                throw new IllegalArgumentException("Fulfilled line has remaining quantity: " + line.id().value());
            }
            if (runtime.status() == OrderLineStatus.OPEN && !runtime.fulfilledQuantity().isZero()) {
                throw new IllegalArgumentException("Open line has fulfilled quantity: " + line.id().value());
            }
            if (runtime.status() == OrderLineStatus.PARTIALLY_FULFILLED
                    && (runtime.fulfilledQuantity().isZero() || remaining.isZero())) {
                throw new IllegalArgumentException("Partially fulfilled line quantity is inconsistent: "
                        + line.id().value());
            }
            if (runtime.lastFulfillmentTick().isPresent()
                    && runtime.lastFulfillmentTick().orElseThrow() > lastUpdatedSimulationTick) {
                throw new IllegalArgumentException("Line fulfillment tick follows order update tick: "
                        + line.id().value());
            }
        }
        if (status == OrderStatus.FULFILLED
                && lines.values().stream().anyMatch(line -> line.status() != OrderLineStatus.FULFILLED)) {
            throw new IllegalArgumentException("Fulfilled order contains incomplete lines");
        }
        if (acceptedSimulationTick.isPresent()
                && acceptedSimulationTick.orElseThrow() < definition.createdSimulationTick()) {
            throw new IllegalArgumentException("Order accepted tick precedes creation");
        }
    }

    private void requireForwardTick(long simulationTick) {
        DomainValidation.requireTick(simulationTick, "Order operation tick");
        if (simulationTick < lastUpdatedSimulationTick) {
            throw new IllegalStateException("Order simulation tick cannot move backward");
        }
    }

    private void validateState() {
        acceptedSimulationTick.ifPresent(tick -> {
            DomainValidation.requireTick(tick, "Order accepted tick");
            if (tick > lastUpdatedSimulationTick) {
                throw new IllegalArgumentException("Order accepted tick follows update tick");
            }
        });
        closedSimulationTick.ifPresent(tick -> {
            DomainValidation.requireTick(tick, "Order closed tick");
            if (tick > lastUpdatedSimulationTick) {
                throw new IllegalArgumentException("Order closed tick follows update tick");
            }
        });
        if (status.isTerminal() != closedSimulationTick.isPresent()) {
            throw new IllegalArgumentException("Order terminal status and closed tick are inconsistent");
        }
        if (status == OrderStatus.SUBMITTED && acceptedSimulationTick.isPresent()) {
            throw new IllegalArgumentException("Submitted order cannot have an accepted tick");
        }
        if ((status == OrderStatus.ACCEPTED || status == OrderStatus.PARTIALLY_FULFILLED
                || status == OrderStatus.FULFILLED) && acceptedSimulationTick.isEmpty()) {
            throw new IllegalArgumentException("Accepted order lifecycle requires an accepted tick");
        }
    }

    private static Map<OrderLineId, OrderLineRuntime> copyLines(Collection<OrderLineRuntime> source) {
        List<OrderLineRuntime> sorted = new ArrayList<>(Objects.requireNonNull(source, "lines"));
        sorted.sort(java.util.Comparator.comparing(OrderLineRuntime::orderLineId));
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("Order runtime requires at least one line");
        }
        Map<OrderLineId, OrderLineRuntime> copied = new LinkedHashMap<>();
        for (OrderLineRuntime line : sorted) {
            OrderLineRuntime snapshot = Objects.requireNonNull(line, "line").snapshot();
            if (copied.putIfAbsent(snapshot.orderLineId(), snapshot) != null) {
                throw new IllegalArgumentException("Duplicate order runtime line: " + snapshot.orderLineId().value());
            }
        }
        return copied;
    }
}
