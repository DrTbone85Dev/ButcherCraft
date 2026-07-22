package com.butchercraft.world.simulation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SimulationScheduler {
    private static final Comparator<ScheduledSimulationEvent> EVENT_ORDER =
            Comparator.comparingLong(ScheduledSimulationEvent::scheduledSimulationTick)
                    .thenComparingInt(event -> event.eventType().executionPriority())
                    .thenComparing(ScheduledSimulationEvent::id);

    private final Map<String, ScheduledSimulationEvent> pendingEvents = new LinkedHashMap<>();

    public static SimulationScheduler empty() {
        return new SimulationScheduler();
    }

    public static SimulationScheduler of(Collection<ScheduledSimulationEvent> events, long currentSimulationTick) {
        Objects.requireNonNull(events, "events");
        SimulationScheduler scheduler = new SimulationScheduler();
        for (ScheduledSimulationEvent event : events) {
            scheduler.schedule(event, currentSimulationTick);
        }
        return scheduler;
    }

    public void schedule(ScheduledSimulationEvent event, long currentSimulationTick) {
        Objects.requireNonNull(event, "event");
        if (currentSimulationTick < 0L) {
            throw new IllegalArgumentException("Current simulation tick must not be negative: " + currentSimulationTick);
        }
        if (event.executionStatus() != SimulationEventStatus.PENDING) {
            throw new IllegalArgumentException("Only pending simulation events can be scheduled: " + event.id());
        }
        if (event.scheduledSimulationTick() < currentSimulationTick) {
            throw new IllegalArgumentException("Cannot schedule simulation event in the past: " + event.id());
        }
        if (pendingEvents.containsKey(event.id())) {
            throw new IllegalArgumentException("Duplicate scheduled simulation event id: " + event.id());
        }
        pendingEvents.put(event.id(), event);
    }

    public Optional<ScheduledSimulationEvent> cancel(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        ScheduledSimulationEvent removed = pendingEvents.remove(eventId);
        return Optional.ofNullable(removed).map(ScheduledSimulationEvent::cancelled);
    }

    public boolean contains(String eventId) {
        return pendingEvents.containsKey(Objects.requireNonNull(eventId, "eventId"));
    }

    public int size() {
        return pendingEvents.size();
    }

    public List<ScheduledSimulationEvent> pendingEvents() {
        return pendingEvents.values().stream()
                .sorted(EVENT_ORDER)
                .toList();
    }

    public Stream<ScheduledSimulationEvent> stream() {
        return pendingEvents().stream();
    }

    Optional<ScheduledSimulationEvent> nextDue(long targetSimulationTick) {
        if (targetSimulationTick < 0L) {
            throw new IllegalArgumentException("Target simulation tick must not be negative: " + targetSimulationTick);
        }
        return pendingEvents.values().stream()
                .filter(event -> event.scheduledSimulationTick() <= targetSimulationTick)
                .min(EVENT_ORDER);
    }

    ScheduledSimulationEvent remove(String eventId) {
        ScheduledSimulationEvent removed = pendingEvents.remove(eventId);
        if (removed == null) {
            throw new IllegalArgumentException("Scheduled simulation event does not exist: " + eventId);
        }
        return removed;
    }

    List<ScheduledSimulationEvent> dueEvents(long targetSimulationTick) {
        if (targetSimulationTick < 0L) {
            throw new IllegalArgumentException("Target simulation tick must not be negative: " + targetSimulationTick);
        }
        return pendingEvents.values().stream()
                .filter(event -> event.scheduledSimulationTick() <= targetSimulationTick)
                .sorted(EVENT_ORDER)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
