package com.butchercraft.world.business.runtime;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.simulation.SimulationCalendar;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BusinessRuntimeManager {
    private final SimulationConfiguration configuration;
    private BusinessRuntimeRegistry registry;

    public BusinessRuntimeManager(BusinessRuntimeRegistry registry, SimulationConfiguration configuration) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.registry.validate(configuration);
    }

    public synchronized List<BusinessRuntimeState> evaluateAt(long simulationTick) {
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Business runtime cannot evaluate a negative simulation tick: " + simulationTick);
        }
        return evaluateAt(
                SimulationCalendar.fromTick(simulationTick, configuration),
                SimulationTime.fromTick(simulationTick, configuration),
                simulationTick
        );
    }

    public synchronized List<BusinessRuntimeState> evaluateAt(
            SimulationCalendar calendar,
            SimulationTime time,
            long simulationTick
    ) {
        Objects.requireNonNull(calendar, "calendar").validate(configuration);
        Objects.requireNonNull(time, "time").validate(configuration);
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Business runtime cannot evaluate a negative simulation tick: " + simulationTick);
        }
        List<BusinessRuntimeState> updated = new ArrayList<>();
        List<BusinessRuntimeState> changed = new ArrayList<>();
        for (BusinessRuntimeState state : registry.states()) {
            BusinessRuntimeState next = evaluateState(state, calendar, time, simulationTick);
            updated.add(next);
            if (!next.equals(state)) {
                changed.add(next);
            }
        }
        registry = BusinessRuntimeRegistry.of(updated);
        registry.validate(configuration);
        return List.copyOf(changed);
    }

    public synchronized BusinessRuntimeState open(BusinessId businessId, String shiftId, long simulationTick) {
        BusinessRuntimeState state = requireState(businessId);
        BusinessShift shift = state.shifts().stream()
                .filter(candidate -> candidate.id().equals(shiftId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Business shift does not exist: "
                        + businessId.value() + "/" + shiftId));
        if (!shift.active()) {
            throw new IllegalArgumentException("Business shift is disabled: " + businessId.value() + "/" + shiftId);
        }
        BusinessRuntimeState updated = state.operatingAt(shift.id(), shift.expectedWorkforce(), simulationTick);
        registry = registry.with(updated);
        registry.validate(configuration);
        return updated;
    }

    public synchronized BusinessRuntimeState close(BusinessId businessId, long simulationTick) {
        BusinessRuntimeState updated = requireState(businessId).closedAt(simulationTick);
        registry = registry.with(updated);
        registry.validate(configuration);
        return updated;
    }

    public synchronized BusinessRuntimeState beginMaintenance(BusinessId businessId, long simulationTick) {
        BusinessRuntimeState updated = requireState(businessId).maintenanceAt(simulationTick);
        registry = registry.with(updated);
        registry.validate(configuration);
        return updated;
    }

    public synchronized BusinessRuntimeState endMaintenance(BusinessId businessId, long simulationTick) {
        BusinessRuntimeState updated = requireState(businessId).closedAt(simulationTick);
        registry = registry.with(updated);
        registry.validate(configuration);
        return updated;
    }

    public synchronized BusinessRuntimeState suspend(BusinessId businessId, long simulationTick) {
        BusinessRuntimeState updated = requireState(businessId).suspendedAt(simulationTick);
        registry = registry.with(updated);
        registry.validate(configuration);
        return updated;
    }

    public synchronized BusinessRuntimeRegistry registry() {
        return registry;
    }

    public synchronized void validateRuntimeState() {
        registry.validate(configuration);
    }

    private BusinessRuntimeState evaluateState(
            BusinessRuntimeState state,
            SimulationCalendar calendar,
            SimulationTime time,
            long simulationTick
    ) {
        if (state.operationalStatus() == BusinessOperationalStatus.SUSPENDED) {
            return state.suspendedAt(simulationTick);
        }
        if (state.maintenance()) {
            return state.maintenanceAt(simulationTick);
        }
        if (!state.businessHours().isOpenAt(calendar, time, configuration)) {
            return state.closedAt(simulationTick);
        }
        return state.activeShiftAt(time, configuration)
                .map(shift -> state.operatingAt(shift.id(), shift.expectedWorkforce(), simulationTick))
                .orElseGet(() -> state.shiftChangeAt(simulationTick));
    }

    private BusinessRuntimeState requireState(BusinessId businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return registry.find(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Business runtime state does not exist: "
                        + businessId.value()));
    }
}
