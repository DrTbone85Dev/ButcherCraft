package com.butchercraft.world.simulation.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SimulationSchedulerRegistryBuilder {
    private final Map<SimulationWorkId, ScheduledSimulationWork> definitions = new LinkedHashMap<>();
    public SimulationSchedulerRegistryBuilder register(ScheduledSimulationWork definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate simulation work id: " + definition.id().value());
        }
        return this;
    }
    public SimulationSchedulerRegistry build() { return SimulationSchedulerRegistry.of(definitions.values()); }
}
