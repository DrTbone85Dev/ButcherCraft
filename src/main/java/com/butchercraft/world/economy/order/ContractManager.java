package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.IndustryId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ContractManager {
    private ContractRegistry registry;
    private final Map<ContractId, EconomicContractRuntime> runtimes = new LinkedHashMap<>();
    private final ContractValidator validator;

    public ContractManager(EconomicActorRegistry actorRegistry) {
        this(ContractRegistry.empty(), List.of(), actorRegistry);
    }

    public ContractManager(
            ContractRegistry loadedRegistry,
            Collection<EconomicContractRuntime> loadedRuntimes,
            EconomicActorRegistry actorRegistry
    ) {
        registry = Objects.requireNonNull(loadedRegistry, "loadedRegistry");
        validator = new ContractValidator(Objects.requireNonNull(actorRegistry, "actorRegistry"));
        for (EconomicContractDefinition definition : registry.definitions()) {
            ContractOperationResult validation = validator.validate(definition);
            if (!validation.success()) {
                throw new IllegalArgumentException("Persisted contract is invalid: "
                        + String.join("; ", validation.messages()));
            }
        }
        for (EconomicContractRuntime runtime : Objects.requireNonNull(loadedRuntimes, "loadedRuntimes")) {
            EconomicContractRuntime snapshot = Objects.requireNonNull(runtime, "runtime").snapshot();
            EconomicContractDefinition definition = registry.find(snapshot.contractId()).orElseThrow(() ->
                    new IllegalArgumentException("Contract runtime references unknown definition: "
                            + snapshot.contractId().value()));
            snapshot.validateAgainst(definition);
            if (runtimes.putIfAbsent(snapshot.contractId(), snapshot) != null) {
                throw new IllegalArgumentException("Duplicate contract runtime: " + snapshot.contractId().value());
            }
        }
        if (runtimes.size() != registry.size()) {
            throw new IllegalArgumentException("Every contract definition requires exactly one runtime");
        }
    }

    public synchronized ContractOperationResult register(EconomicContractDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (registry.contains(definition.id())) {
            return ContractOperationResult.rejected(ContractFailureCode.DUPLICATE_CONTRACT_ID,
                    "Duplicate contract id: " + definition.id().value());
        }
        ContractOperationResult validation = validator.validate(definition);
        if (!validation.success()) return validation;
        registry = registry.withDefinition(definition);
        runtimes.put(definition.id(), EconomicContractRuntime.proposed(definition));
        return ContractOperationResult.accepted();
    }

    public synchronized ContractOperationResult activate(ContractId id, long tick) {
        return transition(id, ContractStatus.ACTIVE, tick, Optional.empty());
    }
    public synchronized ContractOperationResult suspend(ContractId id, long tick, String reason) {
        return transition(id, ContractStatus.SUSPENDED, tick, Optional.ofNullable(reason));
    }
    public synchronized ContractOperationResult resume(ContractId id, long tick) {
        return transition(id, ContractStatus.ACTIVE, tick, Optional.empty());
    }
    public synchronized ContractOperationResult complete(ContractId id, long tick) {
        return transition(id, ContractStatus.COMPLETED, tick, Optional.empty());
    }
    public synchronized ContractOperationResult terminate(ContractId id, long tick, String reason) {
        return transition(id, ContractStatus.TERMINATED, tick, Optional.ofNullable(reason));
    }
    public synchronized ContractOperationResult reject(ContractId id, long tick, String reason) {
        return transition(id, ContractStatus.REJECTED, tick, Optional.ofNullable(reason));
    }
    public synchronized ContractOperationResult expire(ContractId id, long tick) {
        EconomicContractDefinition definition = registry.find(Objects.requireNonNull(id, "id")).orElse(null);
        if (definition != null && definition.expirationSimulationTick().isPresent()
                && tick < definition.expirationSimulationTick().orElseThrow()) {
            return ContractOperationResult.rejected(ContractFailureCode.INVALID_TICK_RANGE,
                    "Contract cannot expire before its expiration tick");
        }
        return transition(id, ContractStatus.EXPIRED, tick, Optional.empty());
    }
    public synchronized ContractOperationResult fail(ContractId id, long tick, String reason) {
        return transition(id, ContractStatus.FAILED, tick, Optional.ofNullable(reason));
    }

    public synchronized ContractOperationResult validateOrderAssociation(EconomicOrderDefinition order) {
        Objects.requireNonNull(order, "order");
        if (order.governingContractId().isEmpty()) return ContractOperationResult.accepted();
        ContractId contractId = order.governingContractId().orElseThrow();
        EconomicContractDefinition contract = registry.find(contractId).orElse(null);
        EconomicContractRuntime runtime = runtimes.get(contractId);
        if (contract == null || runtime == null) {
            return ContractOperationResult.rejected(ContractFailureCode.UNKNOWN_CONTRACT,
                    "Unknown governing contract: " + contractId.value());
        }
        if (runtime.status() != ContractStatus.ACTIVE) {
            return ContractOperationResult.rejected(ContractFailureCode.INVALID_STATUS,
                    "Governing contract is not active: " + contractId.value());
        }
        if (order.createdSimulationTick() < runtime.lastUpdatedSimulationTick()) {
            return ContractOperationResult.rejected(ContractFailureCode.INVALID_TICK_RANGE,
                    "Order creation tick precedes the active contract state");
        }
        boolean partiesMatch = contract.principalActorId().equals(order.requesterActorId())
                || contract.counterpartyActorId().equals(order.requesterActorId());
        if (order.counterpartyActorId().isPresent()) {
            ActorId counterparty = order.counterpartyActorId().orElseThrow();
            partiesMatch &= contract.principalActorId().equals(counterparty)
                    || contract.counterpartyActorId().equals(counterparty);
        }
        if (!partiesMatch) {
            return ContractOperationResult.rejected(ContractFailureCode.CONTRACT_ORDER_MISMATCH,
                    "Order parties do not match governing contract");
        }
        java.util.Set<GoodId> supportedGoods = contract.lines().stream()
                .map(ContractLineDefinition::goodId).collect(java.util.stream.Collectors.toSet());
        if (order.lines().stream().anyMatch(line -> !supportedGoods.contains(line.goodId()))) {
            return ContractOperationResult.rejected(ContractFailureCode.CONTRACT_ORDER_MISMATCH,
                    "Order Good is outside governing contract scope");
        }
        return ContractOperationResult.accepted();
    }

    public synchronized void validateLoadedOrderReferences(OrderRegistry orders) {
        Objects.requireNonNull(orders, "orders");
        for (EconomicContractDefinition contract : registry.definitions()) {
            EconomicContractRuntime runtime = runtimes.get(contract.id());
            for (OrderId orderId : runtime.governedOrderIds()) {
                EconomicOrderDefinition order = orders.find(orderId).orElseThrow(() ->
                        new IllegalArgumentException("Contract references unknown order: " + orderId.value()));
                if (order.governingContractId().isEmpty()
                        || !order.governingContractId().orElseThrow().equals(contract.id())) {
                    throw new IllegalArgumentException("Contract and governed order references disagree: "
                            + contract.id().value() + "/" + orderId.value());
                }
            }
        }
        for (EconomicOrderDefinition order : orders.definitions()) {
            if (order.governingContractId().isEmpty()) continue;
            ContractId contractId = order.governingContractId().orElseThrow();
            EconomicContractDefinition contract = registry.find(contractId).orElseThrow(() ->
                    new IllegalArgumentException("Order references unknown contract: " + contractId.value()));
            if (!runtimes.get(contract.id()).governedOrderIds().contains(order.id())) {
                throw new IllegalArgumentException("Governing contract is missing order reference: "
                        + contractId.value() + "/" + order.id().value());
            }
            validateOrderScope(contract, order);
        }
    }

    synchronized void associateValidatedOrder(EconomicOrderDefinition order) {
        if (order.governingContractId().isEmpty()) return;
        EconomicContractRuntime runtime = runtimes.get(order.governingContractId().orElseThrow());
        if (!runtime.associateOrder(order.id(), order.createdSimulationTick())) {
            throw new IllegalStateException("Order is already associated with governing contract");
        }
    }

    public synchronized Optional<EconomicContractDefinition> find(ContractId id) { return registry.find(id); }
    public synchronized Optional<EconomicContractRuntime> runtimeFor(ContractId id) {
        EconomicContractRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        return runtime == null ? Optional.empty() : Optional.of(runtime.snapshot());
    }
    public synchronized List<EconomicContractDefinition> definitions() { return registry.definitions(); }
    public synchronized List<EconomicContractRuntime> runtimes() {
        return registry.definitions().stream().map(def -> runtimes.get(def.id()).snapshot()).toList();
    }
    public synchronized ContractRegistry registry() { return registry; }
    public synchronized List<EconomicContractDefinition> findByParty(ActorId id) { return registry.findByParty(id); }
    public synchronized List<EconomicContractDefinition> findByPrincipal(ActorId id) { return registry.findByPrincipal(id); }
    public synchronized List<EconomicContractDefinition> findByCounterparty(ActorId id) { return registry.findByCounterparty(id); }
    public synchronized List<EconomicContractDefinition> findByGood(GoodId id) { return registry.findByGood(id); }
    public synchronized List<EconomicContractDefinition> findByType(ContractType type) { return registry.findByType(type); }
    public synchronized List<EconomicContractDefinition> findBySchedule(ContractScheduleType type) {
        return registry.findBySchedule(type);
    }
    public synchronized List<EconomicContractDefinition> findByIndustry(IndustryId id) { return registry.findByIndustry(id); }
    public synchronized List<EconomicContractDefinition> findByStatus(ContractStatus status) {
        Objects.requireNonNull(status, "status");
        return registry.definitions().stream().filter(def -> runtimes.get(def.id()).status() == status).toList();
    }
    public synchronized List<EconomicContractDefinition> activeAt(long simulationTick) {
        DomainValidation.requireTick(simulationTick, "Active contract query tick");
        return registry.activeAt(simulationTick).stream()
                .filter(definition -> runtimes.get(definition.id()).status() == ContractStatus.ACTIVE).toList();
    }
    public synchronized List<EconomicContractDefinition> expiringBetween(long firstInclusive, long lastInclusive) {
        return registry.expiringBetween(firstInclusive, lastInclusive);
    }
    public synchronized List<OrderId> governedOrders(ContractId id) {
        EconomicContractRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        return runtime == null ? List.of() : runtime.governedOrderIds();
    }

    public synchronized ContractTerms termsFor(ContractId id) {
        return registry.find(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown contract: " + id.value())).terms();
    }

    private static void validateOrderScope(
            EconomicContractDefinition contract, EconomicOrderDefinition order
    ) {
        java.util.Set<ActorId> parties = new java.util.HashSet<>();
        parties.add(contract.principalActorId());
        parties.add(contract.counterpartyActorId());
        if (!parties.contains(order.requesterActorId())
                || order.counterpartyActorId().filter(parties::contains).isEmpty()) {
            throw new IllegalArgumentException("Persisted order parties do not match contract: "
                    + order.id().value());
        }
        java.util.Set<GoodId> goods = contract.lines().stream().map(ContractLineDefinition::goodId)
                .collect(java.util.stream.Collectors.toSet());
        if (order.lines().stream().anyMatch(line -> !goods.contains(line.goodId()))) {
            throw new IllegalArgumentException("Persisted order Good is outside contract scope: "
                    + order.id().value());
        }
    }

    private ContractOperationResult transition(
            ContractId id, ContractStatus nextStatus, long tick, Optional<String> reason
    ) {
        EconomicContractRuntime runtime = runtimes.get(Objects.requireNonNull(id, "id"));
        if (runtime == null) {
            return ContractOperationResult.rejected(ContractFailureCode.UNKNOWN_CONTRACT,
                    "Unknown contract: " + id.value());
        }
        if (runtime.status().isTerminal()) {
            return ContractOperationResult.rejected(ContractFailureCode.TERMINAL_CONTRACT,
                    "Terminal contract cannot transition: " + id.value());
        }
        if (tick < runtime.lastUpdatedSimulationTick()) {
            return ContractOperationResult.rejected(ContractFailureCode.INVALID_TICK_RANGE,
                    "Contract operation tick cannot move backward");
        }
        if (!runtime.status().allowedNextStatuses().contains(nextStatus)) {
            return ContractOperationResult.rejected(ContractFailureCode.INVALID_STATUS_TRANSITION,
                    "Invalid contract status transition: " + runtime.status() + " -> " + nextStatus);
        }
        runtime.transitionTo(nextStatus, tick, reason);
        return ContractOperationResult.accepted();
    }
}
