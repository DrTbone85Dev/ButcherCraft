package com.butchercraft.world.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSchedulerTest {
    @Test
    void schedulerExecutesDueEventsInDeterministicOrder() {
        SimulationScheduler scheduler = SimulationScheduler.empty();
        scheduler.schedule(event("weekly_rollover_10", 10L, SimulationEventType.WEEKLY_ROLLOVER), 0L);
        scheduler.schedule(event("daily_rollover_10", 10L, SimulationEventType.DAILY_ROLLOVER), 0L);
        scheduler.schedule(event("yearly_rollover_10", 10L, SimulationEventType.YEARLY_ROLLOVER), 0L);
        scheduler.schedule(event("monthly_rollover_10", 10L, SimulationEventType.MONTHLY_ROLLOVER), 0L);

        List<ScheduledSimulationEvent> due = scheduler.dueEvents(10L);

        assertEquals(List.of(
                "daily_rollover_10",
                "weekly_rollover_10",
                "monthly_rollover_10",
                "yearly_rollover_10"
        ), due.stream().map(ScheduledSimulationEvent::id).toList());
    }

    @Test
    void cancellationRemovesPendingEvent() {
        SimulationScheduler scheduler = SimulationScheduler.empty();
        scheduler.schedule(event("daily_rollover_4", 4L, SimulationEventType.DAILY_ROLLOVER), 0L);

        ScheduledSimulationEvent cancelled = scheduler.cancel("daily_rollover_4").orElseThrow();

        assertEquals(SimulationEventStatus.CANCELLED, cancelled.executionStatus());
        assertFalse(scheduler.contains("daily_rollover_4"));
        assertEquals(0, scheduler.size());
    }

    @Test
    void duplicateAndPastEventsAreRejected() {
        SimulationScheduler scheduler = SimulationScheduler.empty();
        scheduler.schedule(event("daily_rollover_4", 4L, SimulationEventType.DAILY_ROLLOVER), 0L);

        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(event("daily_rollover_4", 5L, SimulationEventType.DAILY_ROLLOVER), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(event("weekly_rollover_1", 1L, SimulationEventType.WEEKLY_ROLLOVER), 2L));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(event("monthly_rollover_8", 8L, SimulationEventType.MONTHLY_ROLLOVER).executed(), 0L));
    }

    @Test
    void nextDueSkipsFutureEvents() {
        SimulationScheduler scheduler = SimulationScheduler.empty();
        scheduler.schedule(event("daily_rollover_5", 5L, SimulationEventType.DAILY_ROLLOVER), 0L);

        assertTrue(scheduler.nextDue(4L).isEmpty());
        assertEquals("daily_rollover_5", scheduler.nextDue(5L).orElseThrow().id());
    }

    private static ScheduledSimulationEvent event(String id, long tick, SimulationEventType type) {
        return new ScheduledSimulationEvent(id, tick, type, "test_payload", SimulationEventStatus.PENDING);
    }
}
