package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.inventory.InventoryRegistry;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.transaction.TransactionStatus;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class OrderManager {
    private OrderRegistry registry;
    private final Map<OrderId, EconomicOrderRuntime> runtimes = new LinkedHashMap<>();
    private final Map<TransactionId, GoodQuantity> allocatedByTransaction = new HashMap<>();
    private final OrderValidator validator;
    private final TransactionManager transactionManager;
    private final ContractManager contractManager;

    public OrderManager(
            EconomicActorRegistry actorRegistry,
            InventoryRegistry inventoryRegistry,
            TransactionManager transactionManager,
            ContractManager contractManager
    ) {
        this(OrderRegistry.empty(), List.of(), actorRegistry, inventoryRegistry, transactionManager, contractManager);
    }

    public OrderManager(
            OrderRegistry loadedRegistry,
            Collection<EconomicOrderRuntime> loadedRuntimes,
            EconomicActorRegistry actorRegistry,
            InventoryRegistry inventoryRegistry,
            TransactionManager transactionManager,
            ContractManager contractManager
    ) {
        registry = Objects.requireNonNull(loadedRegistry, "loadedRegistry");
        validator = new OrderValidator(
                Objects.requireNonNull(actorRegistry, "actorRegistry"),
                Objects.requireNonNull(inventoryRegistry, "inventoryRegistry")
        );
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.contractManager = Objects.requireNonNull(contractManager, "contractManager");
        for (EconomicOrderDefinition definition : registry.definitions()) {
            OrderOperationResult validation = validator.validate(definition);
            if (!validation.success()) {
                throw new IllegalArgumentException("Persisted order is invalid: "
                        + String.join("; ", validation.messages()));
            }
        }
        for (EconomicOrderRuntime runtime : Objects.requireNonNull(loadedRuntimes, "loadedRuntimes")) {
            EconomicOrderRuntime snapshot = Objects.requireNonNull(runtime, "runtime").snapshot();
            EconomicOrderDefinition definition = registry.find(snapshot.orderId()).orElseThrow(() ->
                    new IllegalArgumentException("Order runtime references unknown definition: "
                            + snapshot.orderId().value()));
            boolean overAllowed = definition.governingContractId()
                    .map(contractManager::termsFor).map(ContractTerms::overFulfillmentAllowed).orElse(false);
            snapshot.validateAgainst(definition, overAllowed);
            if (runtimes.putIfAbsent(snapshot.orderId(), snapshot) != null) {
                throw new IllegalArgumentException("Duplicate order runtime: " + snapshot.orderId().value());
            }
            validatePersistedAllocations(definition, snapshot);
        }
        if (runtimes.size() != registry.size()) {
            throw new IllegalArgumentException("Every order definition requires exactly one runtime");
        }
        contractManager.validateLoadedOrderReferences(registry);
    }

    public synchronized OrderOperationResult submit(EconomicOrderDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (registry.contains(definition.id())) {
            return OrderOperationResult.rejected(OrderFailureCode.DUPLICATE_ORDER_ID,
                    "Duplicate order id: " + definition.id().value());
        }
        OrderOperationResult validation = validator.validate(definition);
        if (!validation.success()) return validation;
        ContractOperationResult contractValidation = contractManager.validateOrderAssociation(definition);
        if (!contractValidation.success()) return fromContractFailure(contractValidation);
        registry = registry.withDefinition(definition);
        runtimes.put(definition.id(), EconomicOrderRuntime.submitted(definition));
        contractManager.associateValidatedOrder(definition);
        return OrderOperationResult.accepted();
    }

    public synchronized OrderOperationResult accept(OrderId id, long tick) {
        return transition(id, OrderStatus.ACCEPTED, tick, Optional.empty());
    }

    public synchronized OrderOperationResult reject(OrderId id, long tick, String reason) {
        return transition(id, OrderStatus.REJECTED, tick, Optional.ofNullable(reason));
    }

    public synchronized OrderOperationResult cancel(OrderId id, long tick, String reason) {
        EconomicOrderDefinition definition = registry.find(Objects.requireNonNull(id, "id")).orElse(null);
        if (definition != null && definition.governingContractId().isPresent()
                && !contractManager.termsFor(definition.governingContractId().orElseThrow()).cancellationAllowed()) {
            return OrderOperationResult.rejected(OrderFailureCode.UNSUPPORTED_OPERATION,
                    "Governing contract does not allow cancellation");
        }
        return transition(id, OrderStatus.CANCELLED, tick, Optional.ofNullable(reason));
    }

    public synchronized OrderOperationResult expire(OrderId id, long tick) {
        EconomicOrderDefinition definition = registry.find(Objects.requireNonNull(id, "id")).orElse(null);
        if (definition != null && definition.latestAcceptableFulfillmentTick().isPresent()
                && tick <= definition.latestAcceptableFulfillmentTick().orElseThrow()) {
            return rejected(OrderFailureCode.INVALID_TICK_RANGE,
                    "Order cannot expire before its latest acceptable fulfillment tick");
        }
        return transition(id, OrderStatus.EXPIRED, tick, Optional.empty());
    }

    public synchronized OrderOperationResult fail(OrderId id, long tick, String reason) {
        return transition(id, OrderStatus.FAILED, tick, Optional.ofNullable(reason));
    }

    public synchronized OrderOperationResult recordFulfillment(List<OrderFulfillmentRequest> requests) {
        List<OrderFulfillmentRequest> allocations = List.copyOf(Objects.requireNonNull(requests, "requests"));
        if (allocations.isEmpty()) {
            return OrderOperationResult.rejected(OrderFailureCode.INVALID_QUANTITY,
                    "Fulfillment operation requires at least one allocation");
        }
        Map<OrderId, EconomicOrderRuntime> stagedRuntimes = new LinkedHashMap<>();
        Map<TransactionId, GoodQuantity> stagedTransactionTotals = new HashMap<>(allocatedByTransaction);
        Map<OrderId, Long> stagedTicks = new HashMap<>();
        Set<AllocationKey> operationKeys = new HashSet<>();

        for (OrderFulfillmentRequest request : allocations) {
            OrderOperationResult validation = validateAndStage(
                    request, stagedRuntimes, stagedTransactionTotals, stagedTicks, operationKeys
            );
            if (!validation.success()) return validation;
        }
        for (Map.Entry<OrderId, EconomicOrderRuntime> entry : stagedRuntimes.entrySet()) {
            EconomicOrderRuntime runtime = entry.getValue();
            boolean allFulfilled = runtime.lines().stream()
                    .allMatch(line -> line.status() == OrderLineStatus.FULFILLED);
            runtime.recordFulfillmentApplied(stagedTicks.get(entry.getKey()), allFulfilled);
        }
        stagedRuntimes.forEach(runtimes::put);
        allocatedByTransaction.clear();
        allocatedByTransaction.putAll(stagedTransactionTotals);
        return OrderOperationResult.accepted();
    }

    public synchronized Optional<EconomicOrderDefinition> find(OrderId id) { return registry.find(id); }
    public synchronized Optional<EconomicOrderRuntime> runtimeFor(OrderId id) {
        EconomicOrderRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        return runtime == null ? Optional.empty() : Optional.of(runtime.snapshot());
    }
    public synchronized List<EconomicOrderDefinition> definitions() { return registry.definitions(); }
    public synchronized List<EconomicOrderRuntime> runtimes() {
        return registry.definitions().stream().map(def -> runtimes.get(def.id()).snapshot()).toList();
    }
    public synchronized OrderRegistry registry() { return registry; }
    public synchronized List<EconomicOrderDefinition> findByRequester(ActorId id) { return registry.findByRequester(id); }
    public synchronized List<EconomicOrderDefinition> findByCounterparty(ActorId id) { return registry.findByCounterparty(id); }
    public synchronized List<EconomicOrderDefinition> findByParty(ActorId id) { return registry.findByParty(id); }
    public synchronized List<EconomicOrderDefinition> findByGood(GoodId id) { return registry.findByGood(id); }
    public synchronized List<EconomicOrderDefinition> findByType(OrderType type) { return registry.findByType(type); }
    public synchronized List<EconomicOrderDefinition> findByContract(ContractId id) { return registry.findByContract(id); }
    public synchronized List<EconomicOrderDefinition> findCreatedBetween(long first, long last) {
        return registry.findCreatedBetween(first, last);
    }
    public synchronized List<EconomicOrderDefinition> findRequestedBetween(long first, long last) {
        return registry.findRequestedBetween(first, last);
    }
    public synchronized List<EconomicOrderDefinition> findByStatus(OrderStatus status) {
        Objects.requireNonNull(status, "status");
        return registry.definitions().stream().filter(def -> runtimes.get(def.id()).status() == status).toList();
    }
    public synchronized List<EconomicOrderDefinition> openOrders() {
        return registry.definitions().stream().filter(def -> !runtimes.get(def.id()).status().isTerminal()).toList();
    }
    public synchronized List<EconomicOrderDefinition> fulfillableOrders() {
        return registry.definitions().stream().filter(def -> runtimes.get(def.id()).status().isFulfillable()).toList();
    }
    public synchronized List<EconomicOrderDefinition> overdueOrders(long simulationTick) {
        DomainValidation.requireTick(simulationTick, "Overdue query tick");
        return registry.definitions().stream().filter(def -> !runtimes.get(def.id()).status().isTerminal()
                && def.latestAcceptableFulfillmentTick().isPresent()
                && def.latestAcceptableFulfillmentTick().orElseThrow() < simulationTick).toList();
    }
    public synchronized List<EconomicOrderDefinition> findByLine(OrderLineId lineId) {
        Objects.requireNonNull(lineId, "lineId");
        return registry.definitions().stream().filter(def -> def.findLine(lineId).isPresent()).toList();
    }
    public synchronized GoodQuantity fulfilledQuantity(OrderId orderId, OrderLineId lineId) {
        return requireRuntime(orderId).findLine(lineId).orElseThrow(() ->
                new IllegalArgumentException("Unknown order line: " + lineId.value())).fulfilledQuantity();
    }
    public synchronized GoodQuantity remainingQuantity(OrderId orderId, OrderLineId lineId) {
        EconomicOrderDefinition definition = registry.find(orderId).orElseThrow(() ->
                new IllegalArgumentException("Unknown order: " + orderId.value()));
        OrderLineDefinition line = definition.findLine(lineId).orElseThrow(() ->
                new IllegalArgumentException("Unknown order line: " + lineId.value()));
        return requireRuntime(orderId).findLine(lineId).orElseThrow().remainingQuantity(line.requestedQuantity());
    }

    private OrderOperationResult validateAndStage(
            OrderFulfillmentRequest request,
            Map<OrderId, EconomicOrderRuntime> stagedRuntimes,
            Map<TransactionId, GoodQuantity> stagedTransactionTotals,
            Map<OrderId, Long> stagedTicks,
            Set<AllocationKey> operationKeys
    ) {
        AllocationKey key = new AllocationKey(request.orderId(), request.orderLineId(), request.transactionId());
        if (!operationKeys.add(key)) {
            return rejected(OrderFailureCode.DUPLICATE_TRANSACTION_ALLOCATION,
                    "Duplicate transaction allocation in operation");
        }
        EconomicOrderDefinition definition = registry.find(request.orderId()).orElse(null);
        if (definition == null) return rejected(OrderFailureCode.UNKNOWN_ORDER,
                "Unknown order: " + request.orderId().value());
        EconomicOrderRuntime runtime = stagedRuntimes.computeIfAbsent(request.orderId(),
                ignored -> requireRuntime(request.orderId()).snapshot());
        if (!runtime.status().isFulfillable()) {
            return rejected(runtime.status().isTerminal() ? OrderFailureCode.TERMINAL_ORDER : OrderFailureCode.INVALID_STATUS,
                    "Order is not fulfillable: " + request.orderId().value());
        }
        OrderLineDefinition lineDefinition = definition.findLine(request.orderLineId()).orElse(null);
        OrderLineRuntime lineRuntime = runtime.mutableLine(request.orderLineId());
        if (lineDefinition == null || lineRuntime == null) return rejected(OrderFailureCode.UNKNOWN_ORDER_LINE,
                "Unknown order line: " + request.orderLineId().value());
        EconomicTransaction transaction = transactionManager.find(request.transactionId()).orElse(null);
        if (transaction == null) return rejected(OrderFailureCode.UNKNOWN_TRANSACTION,
                "Unknown transaction: " + request.transactionId().value());
        if (transaction.status() != TransactionStatus.APPLIED) return rejected(OrderFailureCode.TRANSACTION_NOT_APPLIED,
                "Only applied transactions may fulfill orders");
        if (!transaction.goodId().equals(lineDefinition.goodId())) return rejected(
                OrderFailureCode.TRANSACTION_GOOD_MISMATCH, "Transaction Good does not match order line");
        if (transaction.unitOfMeasure() != lineDefinition.unitOfMeasure()) return rejected(
                OrderFailureCode.UNIT_MISMATCH, "Transaction unit does not match order line");
        long currentTick = stagedTicks.getOrDefault(request.orderId(), runtime.lastUpdatedSimulationTick());
        if (request.simulationTick() < transaction.simulationTick() || request.simulationTick() < currentTick) {
            return rejected(OrderFailureCode.INVALID_TICK_RANGE, "Fulfillment tick cannot move backward");
        }
        if (lineRuntime.allocations().stream()
                .anyMatch(existing -> existing.transactionId().equals(request.transactionId()))) {
            return rejected(OrderFailureCode.DUPLICATE_TRANSACTION_ALLOCATION,
                    "Transaction is already allocated to this order line");
        }
        GoodQuantity nextTransactionTotal = stagedTransactionTotals
                .getOrDefault(request.transactionId(), GoodQuantity.zero()).add(request.quantity());
        if (nextTransactionTotal.compareTo(GoodQuantity.of(transaction.quantity())) > 0) {
            return rejected(OrderFailureCode.TRANSACTION_QUANTITY_EXCEEDED,
                    "Transaction fulfillment allocation exceeds authoritative quantity");
        }
        boolean overAllowed = definition.governingContractId()
                .map(contractManager::termsFor).map(ContractTerms::overFulfillmentAllowed).orElse(false);
        GoodQuantity nextLineTotal = lineRuntime.fulfilledQuantity().add(request.quantity());
        if (!overAllowed && nextLineTotal.compareTo(lineDefinition.requestedQuantity()) > 0) {
            return rejected(OrderFailureCode.OVER_FULFILLMENT,
                    "Fulfillment exceeds requested line quantity");
        }
        lineRuntime.applyAllocation(
                new OrderFulfillmentAllocation(request.transactionId(), request.quantity(), request.simulationTick()),
                lineDefinition.requestedQuantity(), overAllowed
        );
        stagedTransactionTotals.put(request.transactionId(), nextTransactionTotal);
        stagedTicks.put(request.orderId(), request.simulationTick());
        return OrderOperationResult.accepted();
    }

    private void validatePersistedAllocations(
            EconomicOrderDefinition definition, EconomicOrderRuntime runtime
    ) {
        for (OrderLineRuntime lineRuntime : runtime.lines()) {
            OrderLineDefinition line = definition.findLine(lineRuntime.orderLineId()).orElseThrow();
            for (OrderFulfillmentAllocation allocation : lineRuntime.allocations()) {
                EconomicTransaction transaction = transactionManager.find(allocation.transactionId()).orElseThrow(() ->
                        new IllegalArgumentException("Order allocation references unknown transaction: "
                                + allocation.transactionId().value()));
                if (transaction.status() != TransactionStatus.APPLIED
                        || !transaction.goodId().equals(line.goodId())
                        || transaction.unitOfMeasure() != line.unitOfMeasure()
                        || allocation.simulationTick() < transaction.simulationTick()) {
                    throw new IllegalArgumentException("Persisted order allocation is incompatible with transaction: "
                            + allocation.transactionId().value());
                }
                GoodQuantity total = allocatedByTransaction.getOrDefault(
                        allocation.transactionId(), GoodQuantity.zero()
                ).add(allocation.quantity());
                if (total.compareTo(GoodQuantity.of(transaction.quantity())) > 0) {
                    throw new IllegalArgumentException("Persisted transaction allocation exceeds quantity: "
                            + allocation.transactionId().value());
                }
                allocatedByTransaction.put(allocation.transactionId(), total);
            }
        }
    }

    private OrderOperationResult transition(
            OrderId id, OrderStatus nextStatus, long tick, Optional<String> reason
    ) {
        EconomicOrderRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        if (runtime == null) return rejected(OrderFailureCode.UNKNOWN_ORDER, "Unknown order: " + id.value());
        if (runtime.status().isTerminal()) return rejected(OrderFailureCode.TERMINAL_ORDER,
                "Terminal order cannot transition: " + id.value());
        if (tick < runtime.lastUpdatedSimulationTick()) return rejected(OrderFailureCode.INVALID_TICK_RANGE,
                "Order operation tick cannot move backward");
        if (!runtime.status().allowedNextStatuses().contains(nextStatus)) return rejected(
                OrderFailureCode.INVALID_STATUS_TRANSITION,
                "Invalid order status transition: " + runtime.status() + " -> " + nextStatus);
        runtime.transitionTo(nextStatus, tick, reason);
        return OrderOperationResult.accepted();
    }

    private EconomicOrderRuntime requireRuntime(OrderId id) {
        EconomicOrderRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        if (runtime == null) throw new IllegalArgumentException("Unknown order: " + id.value());
        return runtime;
    }

    private static OrderOperationResult fromContractFailure(ContractOperationResult result) {
        ContractFailureCode code = result.failureCode().orElse(ContractFailureCode.UNKNOWN);
        OrderFailureCode mapped = switch (code) {
            case UNKNOWN_CONTRACT -> OrderFailureCode.UNKNOWN_CONTRACT;
            case INVALID_STATUS -> OrderFailureCode.CONTRACT_NOT_ACTIVE;
            case CONTRACT_ORDER_MISMATCH -> OrderFailureCode.CONTRACT_SCOPE_MISMATCH;
            default -> OrderFailureCode.VALIDATION_FAILED;
        };
        return OrderOperationResult.rejected(mapped, String.join("; ", result.messages()));
    }

    private static OrderOperationResult rejected(OrderFailureCode code, String message) {
        return OrderOperationResult.rejected(code, message);
    }

    private record AllocationKey(OrderId orderId, OrderLineId lineId, TransactionId transactionId) {
    }
}
