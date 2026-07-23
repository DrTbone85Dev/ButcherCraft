package com.butchercraft.world.simulation.scheduler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SimulationWorkHandlerRegistry {
    private final Map<SimulationWorkTypeId, SimulationWorkHandler> handlers;
    public SimulationWorkHandlerRegistry(Collection<? extends SimulationWorkHandler> source) {
        List<? extends SimulationWorkHandler> ordered = Objects.requireNonNull(source, "handlers").stream()
                .map(handler -> Objects.requireNonNull(handler, "handler"))
                .sorted(java.util.Comparator.comparing(SimulationWorkHandler::supportedTypeId)).toList();
        Map<SimulationWorkTypeId, SimulationWorkHandler> mutable = new LinkedHashMap<>();
        for (SimulationWorkHandler handler : ordered) {
            Objects.requireNonNull(handler.supportedTypeId(), "handler supported type id");
            Objects.requireNonNull(handler.effectType(), "handler effect type");
            if (mutable.putIfAbsent(handler.supportedTypeId(), handler) != null) {
                throw new IllegalArgumentException("Duplicate work handler: " + handler.supportedTypeId().value());
            }
        }
        handlers = java.util.Collections.unmodifiableMap(mutable);
    }
    public static SimulationWorkHandlerRegistry empty() { return new SimulationWorkHandlerRegistry(List.of()); }
    public boolean contains(SimulationWorkTypeId id) { return handlers.containsKey(Objects.requireNonNull(id, "id")); }
    public Optional<SimulationWorkHandler> find(SimulationWorkTypeId id) {
        return Optional.ofNullable(handlers.get(Objects.requireNonNull(id, "id")));
    }
    public List<SimulationWorkHandler> handlers() { return List.copyOf(handlers.values()); }
    public int size() { return handlers.size(); }
}
