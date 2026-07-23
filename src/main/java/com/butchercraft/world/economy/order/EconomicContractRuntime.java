package com.butchercraft.world.economy.order;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public final class EconomicContractRuntime {
    private final ContractId contractId;
    private ContractStatus status;
    private long lastUpdatedSimulationTick;
    private OptionalLong activationSimulationTick;
    private OptionalLong terminationSimulationTick;
    private final Set<OrderId> governedOrderIds;
    private Optional<String> currentPeriodIdentity;
    private Optional<String> statusReason;
    private long revision;
    private final int schemaVersion;

    public EconomicContractRuntime(
            ContractId contractId,
            ContractStatus status,
            long lastUpdatedSimulationTick,
            OptionalLong activationSimulationTick,
            OptionalLong terminationSimulationTick,
            Collection<OrderId> governedOrderIds,
            Optional<String> currentPeriodIdentity,
            Optional<String> statusReason,
            long revision,
            int schemaVersion
    ) {
        this.contractId = Objects.requireNonNull(contractId, "contractId");
        this.status = Objects.requireNonNull(status, "status");
        this.lastUpdatedSimulationTick = DomainValidation.requireTick(
                lastUpdatedSimulationTick, "Contract runtime update tick"
        );
        this.activationSimulationTick = Objects.requireNonNull(activationSimulationTick, "activationSimulationTick");
        this.terminationSimulationTick = Objects.requireNonNull(
                terminationSimulationTick, "terminationSimulationTick"
        );
        this.governedOrderIds = copyOrderIds(governedOrderIds);
        this.currentPeriodIdentity = DomainValidation.optionalText(
                currentPeriodIdentity, "Contract current period identity", 128
        );
        this.statusReason = DomainValidation.optionalText(statusReason, "Contract status reason", 512);
        if (revision < 0L) {
            throw new IllegalArgumentException("Contract runtime revision must not be negative");
        }
        this.revision = revision;
        this.schemaVersion = DomainValidation.requireSchema(schemaVersion, "contract runtime");
        validateState();
    }

    public static EconomicContractRuntime proposed(EconomicContractDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new EconomicContractRuntime(
                definition.id(), ContractStatus.PROPOSED, definition.effectiveSimulationTick(),
                OptionalLong.empty(), OptionalLong.empty(), List.of(), Optional.empty(), Optional.empty(),
                0L, OrderContractSchema.CURRENT_VERSION
        );
    }

    public synchronized ContractId contractId() { return contractId; }
    public synchronized ContractStatus status() { return status; }
    public synchronized long lastUpdatedSimulationTick() { return lastUpdatedSimulationTick; }
    public synchronized OptionalLong activationSimulationTick() { return activationSimulationTick; }
    public synchronized OptionalLong terminationSimulationTick() { return terminationSimulationTick; }
    public synchronized List<OrderId> governedOrderIds() { return List.copyOf(governedOrderIds); }
    public synchronized Optional<String> currentPeriodIdentity() { return currentPeriodIdentity; }
    public synchronized Optional<String> statusReason() { return statusReason; }
    public synchronized long revision() { return revision; }
    public int schemaVersion() { return schemaVersion; }

    public synchronized EconomicContractRuntime snapshot() {
        return new EconomicContractRuntime(
                contractId, status, lastUpdatedSimulationTick, activationSimulationTick,
                terminationSimulationTick, governedOrderIds, currentPeriodIdentity, statusReason,
                revision, schemaVersion
        );
    }

    synchronized void transitionTo(ContractStatus nextStatus, long simulationTick, Optional<String> reason) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        requireForwardTick(simulationTick);
        if (!status.allowedNextStatuses().contains(nextStatus)) {
            throw new IllegalStateException("Invalid contract status transition: " + status + " -> " + nextStatus);
        }
        status = nextStatus;
        lastUpdatedSimulationTick = simulationTick;
        revision++;
        statusReason = DomainValidation.optionalText(reason, "Contract status reason", 512);
        if (nextStatus == ContractStatus.ACTIVE && activationSimulationTick.isEmpty()) {
            activationSimulationTick = OptionalLong.of(simulationTick);
        }
        if (nextStatus.isTerminal()) {
            terminationSimulationTick = OptionalLong.of(simulationTick);
        }
    }

    synchronized boolean associateOrder(OrderId orderId, long simulationTick) {
        requireForwardTick(simulationTick);
        boolean added = governedOrderIds.add(Objects.requireNonNull(orderId, "orderId"));
        if (added) {
            lastUpdatedSimulationTick = simulationTick;
            revision++;
        }
        return added;
    }

    public synchronized void validateAgainst(EconomicContractDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!contractId.equals(definition.id())) {
            throw new IllegalArgumentException("Contract runtime definition id mismatch");
        }
        if (lastUpdatedSimulationTick < definition.effectiveSimulationTick()) {
            throw new IllegalArgumentException("Contract runtime update tick precedes effective tick");
        }
        if (activationSimulationTick.isPresent()
                && activationSimulationTick.orElseThrow() < definition.effectiveSimulationTick()) {
            throw new IllegalArgumentException("Contract activation tick precedes effective tick");
        }
    }

    private void requireForwardTick(long simulationTick) {
        DomainValidation.requireTick(simulationTick, "Contract operation tick");
        if (simulationTick < lastUpdatedSimulationTick) {
            throw new IllegalStateException("Contract simulation tick cannot move backward");
        }
    }

    private void validateState() {
        activationSimulationTick.ifPresent(tick -> {
            DomainValidation.requireTick(tick, "Contract activation tick");
            if (tick > lastUpdatedSimulationTick) {
                throw new IllegalArgumentException("Contract activation tick follows update tick");
            }
        });
        terminationSimulationTick.ifPresent(tick -> {
            DomainValidation.requireTick(tick, "Contract termination tick");
            if (tick > lastUpdatedSimulationTick) {
                throw new IllegalArgumentException("Contract termination tick follows update tick");
            }
        });
        if (status.isTerminal() != terminationSimulationTick.isPresent()) {
            throw new IllegalArgumentException("Contract terminal status and termination tick are inconsistent");
        }
        if ((status == ContractStatus.ACTIVE || status == ContractStatus.SUSPENDED
                || status == ContractStatus.COMPLETED) && activationSimulationTick.isEmpty()) {
            throw new IllegalArgumentException("Active contract lifecycle requires an activation tick");
        }
    }

    private static Set<OrderId> copyOrderIds(Collection<OrderId> source) {
        LinkedHashSet<OrderId> copied = new LinkedHashSet<>();
        for (OrderId id : Objects.requireNonNull(source, "governedOrderIds")) {
            if (!copied.add(Objects.requireNonNull(id, "governedOrderId"))) {
                throw new IllegalArgumentException("Duplicate governed order id: " + id.value());
            }
        }
        return copied;
    }
}
