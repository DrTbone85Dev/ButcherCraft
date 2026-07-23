package com.butchercraft.world.simulation.scheduler;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SimulationStageRegistry {
    private final List<SimulationStageDefinition> definitions;
    private final Map<SimulationStageId, SimulationStageDefinition> byId;
    private SimulationStageRegistry(Collection<SimulationStageDefinition> source) {
        definitions = Objects.requireNonNull(source, "definitions").stream()
                .map(definition -> Objects.requireNonNull(definition, "definition"))
                .sorted(java.util.Comparator.comparingInt(SimulationStageDefinition::executionOrder)
                        .thenComparing(SimulationStageDefinition::id)).toList();
        Set<SimulationStageId> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (SimulationStageDefinition definition : definitions) {
            if (!ids.add(definition.id())) throw new IllegalArgumentException("Duplicate stage id: " + definition.id());
            if (!orders.add(definition.executionOrder())) {
                throw new IllegalArgumentException("Duplicate stage execution order: " + definition.executionOrder());
            }
        }
        byId = definitions.stream().collect(Collectors.toUnmodifiableMap(
                SimulationStageDefinition::id, Function.identity()
        ));
    }
    public static SimulationStageRegistry of(Collection<SimulationStageDefinition> source) {
        return new SimulationStageRegistry(source);
    }
    public static SimulationStageRegistry builtIn() { return of(BuiltInSimulationStages.definitions()); }
    public static SimulationStageRegistryBuilder builder() { return new SimulationStageRegistryBuilder(); }
    public Optional<SimulationStageDefinition> find(SimulationStageId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }
    public boolean contains(SimulationStageId id) { return byId.containsKey(Objects.requireNonNull(id, "id")); }
    public List<SimulationStageDefinition> definitions() { return definitions; }
    public int size() { return definitions.size(); }
}
