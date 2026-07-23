package com.butchercraft.world.economy.actor;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.workforce.WorkforceDefinitionId;

import java.util.Objects;
import java.util.Optional;

public final class EconomicActorRuntime {
    private final ActorId actorId;
    private ActorRuntimeStatus runtimeStatus;
    private boolean enabled;
    private boolean operational;
    private Optional<BusinessId> assignedBusinessRuntime;
    private Optional<WorkforceDefinitionId> assignedWorkforce;
    private long lastSimulationTick;

    public EconomicActorRuntime(
            ActorId actorId,
            ActorRuntimeStatus runtimeStatus,
            boolean enabled,
            boolean operational,
            Optional<BusinessId> assignedBusinessRuntime,
            Optional<WorkforceDefinitionId> assignedWorkforce,
            long lastSimulationTick
    ) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.runtimeStatus = Objects.requireNonNull(runtimeStatus, "runtimeStatus");
        this.enabled = enabled;
        this.operational = operational;
        this.assignedBusinessRuntime = Objects.requireNonNull(assignedBusinessRuntime, "assignedBusinessRuntime");
        this.assignedWorkforce = Objects.requireNonNull(assignedWorkforce, "assignedWorkforce");
        this.lastSimulationTick = requireTick(lastSimulationTick);
        validate();
    }

    public static EconomicActorRuntime available(ActorId actorId) {
        return new EconomicActorRuntime(
                actorId,
                ActorRuntimeStatus.AVAILABLE,
                true,
                false,
                Optional.empty(),
                Optional.empty(),
                0L
        );
    }

    public synchronized ActorId actorId() {
        return actorId;
    }

    public synchronized ActorRuntimeStatus runtimeStatus() {
        return runtimeStatus;
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public synchronized boolean operational() {
        return operational;
    }

    public synchronized Optional<BusinessId> assignedBusinessRuntime() {
        return assignedBusinessRuntime;
    }

    public synchronized Optional<WorkforceDefinitionId> assignedWorkforce() {
        return assignedWorkforce;
    }

    public synchronized long lastSimulationTick() {
        return lastSimulationTick;
    }

    public synchronized void transitionTo(ActorRuntimeStatus status, long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        runtimeStatus = Objects.requireNonNull(status, "status");
        enabled = status.enabled();
        operational = status.operational();
        lastSimulationTick = simulationTick;
    }

    public synchronized void assignBusinessRuntime(BusinessId businessId, long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        assignedBusinessRuntime = Optional.of(Objects.requireNonNull(businessId, "businessId"));
        lastSimulationTick = simulationTick;
    }

    public synchronized void clearBusinessRuntime(long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        assignedBusinessRuntime = Optional.empty();
        lastSimulationTick = simulationTick;
    }

    public synchronized void assignWorkforce(WorkforceDefinitionId workforceId, long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        assignedWorkforce = Optional.of(Objects.requireNonNull(workforceId, "workforceId"));
        lastSimulationTick = simulationTick;
    }

    public synchronized void clearWorkforce(long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        assignedWorkforce = Optional.empty();
        lastSimulationTick = simulationTick;
    }

    public synchronized void validate() {
        if (enabled != runtimeStatus.enabled() || operational != runtimeStatus.operational()) {
            throw new IllegalArgumentException("Economic actor runtime flags do not match status: " + actorId.value());
        }
        requireTick(lastSimulationTick);
    }

    private void requireCurrentOrFutureTick(long simulationTick) {
        requireTick(simulationTick);
        if (simulationTick < lastSimulationTick) {
            throw new IllegalArgumentException("Economic actor runtime tick must not move backward: " + actorId.value());
        }
    }

    private static long requireTick(long simulationTick) {
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Economic actor simulation tick must not be negative: " + simulationTick);
        }
        return simulationTick;
    }
}
