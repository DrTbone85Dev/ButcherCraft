package com.butchercraft.world.simulation.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SimulationStageRegistryBuilder {
    private final Map<SimulationStageId, SimulationStageDefinition> definitions = new LinkedHashMap<>();
    public SimulationStageRegistryBuilder register(SimulationStageDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate simulation stage id: " + definition.id().value());
        }
        return this;
    }
    public SimulationStageRegistry build() { return SimulationStageRegistry.of(definitions.values()); }
}
