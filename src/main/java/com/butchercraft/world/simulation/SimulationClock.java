package com.butchercraft.world.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SimulationClock {
    private static final String ROLLOVER_PAYLOAD_REFERENCE = "simulation_calendar";

    private final SimulationConfiguration configuration;
    private final SimulationScheduler scheduler;
    private final SimulationEventBus eventBus;
    private long simulationTick;

    public SimulationClock(SimulationConfiguration configuration) {
        this(configuration, SimulationState.initial(configuration), new SimulationEventBus());
    }

    public SimulationClock(
            SimulationConfiguration configuration,
            SimulationState state,
            SimulationEventBus eventBus
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(state, "state").validate(configuration);
        this.scheduler = SimulationScheduler.of(state.pendingEvents(), state.simulationTick());
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.simulationTick = state.simulationTick();
        ensureRolloverEvents();
    }

    public synchronized List<ScheduledSimulationEvent> advance(long simulationTicks) {
        if (simulationTicks < 0L) {
            throw new IllegalArgumentException("Cannot advance simulation by negative ticks: " + simulationTicks);
        }
        if (simulationTicks == 0L) {
            return List.of();
        }
        long targetTick = Math.addExact(simulationTick, simulationTicks);
        List<ScheduledSimulationEvent> executed = new ArrayList<>();
        while (true) {
            Optional<ScheduledSimulationEvent> due = scheduler.nextDue(targetTick);
            if (due.isEmpty()) {
                break;
            }
            ScheduledSimulationEvent pending = scheduler.remove(due.get().id());
            ScheduledSimulationEvent executedEvent = pending.executed();
            executed.add(executedEvent);
            eventBus.publish(executedEvent);
            scheduleNextRollover(pending);
        }
        simulationTick = targetTick;
        ensureRolloverEvents();
        return List.copyOf(executed);
    }

    public synchronized ScheduledSimulationEvent schedule(
            String eventId,
            long scheduledSimulationTick,
            SimulationEventType eventType,
            String payloadReference
    ) {
        ScheduledSimulationEvent event = new ScheduledSimulationEvent(
                eventId,
                scheduledSimulationTick,
                eventType,
                payloadReference,
                SimulationEventStatus.PENDING
        );
        scheduler.schedule(event, simulationTick);
        return event;
    }

    public synchronized Optional<ScheduledSimulationEvent> cancel(String eventId) {
        return scheduler.cancel(eventId);
    }

    public synchronized long simulationTick() {
        return simulationTick;
    }

    public synchronized SimulationTime time() {
        return SimulationTime.fromTick(simulationTick, configuration);
    }

    public synchronized SimulationCalendar calendar() {
        return SimulationCalendar.fromTick(simulationTick, configuration);
    }

    public SimulationConfiguration configuration() {
        return configuration;
    }

    public SimulationEventBus eventBus() {
        return eventBus;
    }

    public synchronized List<ScheduledSimulationEvent> pendingEvents() {
        return scheduler.pendingEvents();
    }

    public synchronized SimulationState state() {
        return new SimulationState(
                SimulationSchema.CURRENT_VERSION,
                simulationTick,
                calendar(),
                scheduler.pendingEvents()
        );
    }

    private void ensureRolloverEvents() {
        scheduleRolloverIfMissing(SimulationEventType.DAILY_ROLLOVER, configuration.ticksPerDay());
        scheduleRolloverIfMissing(SimulationEventType.WEEKLY_ROLLOVER, configuration.ticksPerWeek());
        scheduleRolloverIfMissing(SimulationEventType.MONTHLY_ROLLOVER, configuration.ticksPerMonth());
        scheduleRolloverIfMissing(SimulationEventType.YEARLY_ROLLOVER, configuration.ticksPerYear());
    }

    private void scheduleNextRollover(ScheduledSimulationEvent event) {
        rolloverPeriod(event.eventType()).ifPresent(period -> {
            long nextTick = Math.addExact(event.scheduledSimulationTick(), period);
            scheduleRollover(event.eventType(), nextTick);
        });
    }

    private void scheduleRolloverIfMissing(SimulationEventType eventType, long period) {
        long nextTick = nextBoundary(simulationTick, period);
        scheduleRollover(eventType, nextTick);
    }

    private void scheduleRollover(SimulationEventType eventType, long scheduledTick) {
        String eventId = rolloverEventId(eventType, scheduledTick);
        if (!scheduler.contains(eventId)) {
            scheduler.schedule(new ScheduledSimulationEvent(
                    eventId,
                    scheduledTick,
                    eventType,
                    ROLLOVER_PAYLOAD_REFERENCE,
                    SimulationEventStatus.PENDING
            ), simulationTick);
        }
    }

    private Optional<Long> rolloverPeriod(SimulationEventType eventType) {
        return switch (eventType) {
            case DAILY_ROLLOVER -> Optional.of(configuration.ticksPerDay());
            case WEEKLY_ROLLOVER -> Optional.of(configuration.ticksPerWeek());
            case MONTHLY_ROLLOVER -> Optional.of(configuration.ticksPerMonth());
            case YEARLY_ROLLOVER -> Optional.of(configuration.ticksPerYear());
        };
    }

    private static long nextBoundary(long currentTick, long period) {
        long remainder = currentTick % period;
        return remainder == 0L
                ? Math.addExact(currentTick, period)
                : Math.addExact(currentTick, period - remainder);
    }

    private static String rolloverEventId(SimulationEventType eventType, long scheduledTick) {
        return eventType.serializedName() + "_" + scheduledTick;
    }
}
