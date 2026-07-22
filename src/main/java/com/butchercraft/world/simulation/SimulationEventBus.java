package com.butchercraft.world.simulation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimulationEventBus {
    private final List<SimulationEventListener> globalListeners = new CopyOnWriteArrayList<>();
    private final Map<SimulationEventType, List<SimulationEventListener>> listenersByType =
            new EnumMap<>(SimulationEventType.class);

    public SimulationEventBus() {
        for (SimulationEventType type : SimulationEventType.values()) {
            listenersByType.put(type, new CopyOnWriteArrayList<>());
        }
    }

    public void subscribe(SimulationEventListener listener) {
        globalListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void subscribe(SimulationEventType eventType, SimulationEventListener listener) {
        Objects.requireNonNull(eventType, "eventType");
        listenersByType.get(eventType).add(Objects.requireNonNull(listener, "listener"));
    }

    public void unsubscribe(SimulationEventListener listener) {
        Objects.requireNonNull(listener, "listener");
        globalListeners.remove(listener);
        listenersByType.values().forEach(listeners -> listeners.remove(listener));
    }

    public void publish(ScheduledSimulationEvent event) {
        Objects.requireNonNull(event, "event");
        List<SimulationEventListener> listeners = new ArrayList<>(globalListeners);
        listeners.addAll(listenersByType.get(event.eventType()));
        for (SimulationEventListener listener : listeners) {
            listener.onSimulationEvent(event);
        }
    }
}
