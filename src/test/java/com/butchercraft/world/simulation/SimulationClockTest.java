package com.butchercraft.world.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationClockTest {
    private static final SimulationConfiguration CONFIGURATION =
            new SimulationConfiguration(2, 3, 2, 2, 2, 4);

    @Test
    void clockAdvancesSimulationTimeFromConfiguration() {
        SimulationClock clock = new SimulationClock(CONFIGURATION);

        clock.advance(2L);

        assertEquals(2L, clock.simulationTick());
        assertEquals(new SimulationTime(2L, 1L, 0, 1), clock.time());
        assertEquals(new SimulationCalendar(1, 1, 1, 1, 1, Season.SPRING), clock.calendar());
    }

    @Test
    void calendarRollsOverDayWeekMonthAndYear() {
        SimulationClock clock = new SimulationClock(CONFIGURATION);

        clock.advance(CONFIGURATION.ticksPerDay());
        assertEquals(new SimulationCalendar(2, 1, 1, 1, 2, Season.SPRING), clock.calendar());

        clock.advance(CONFIGURATION.ticksPerDay());
        assertEquals(new SimulationCalendar(3, 2, 1, 1, 1, Season.SPRING), clock.calendar());

        clock.advance(CONFIGURATION.ticksPerMonth() - (2L * CONFIGURATION.ticksPerDay()));
        assertEquals(new SimulationCalendar(1, 1, 2, 1, 1, Season.SUMMER), clock.calendar());

        clock.advance(CONFIGURATION.ticksPerYear() - CONFIGURATION.ticksPerMonth());
        assertEquals(new SimulationCalendar(1, 1, 1, 2, 1, Season.SPRING), clock.calendar());
    }

    @Test
    void deterministicProgressionProducesSameState() {
        SimulationClock first = new SimulationClock(CONFIGURATION);
        SimulationClock second = new SimulationClock(CONFIGURATION);

        first.advance(137L);
        second.advance(100L);
        second.advance(37L);

        assertEquals(first.state(), second.state());
    }

    @Test
    void rolloverEventsPublishInDeterministicOrder() {
        SimulationEventBus bus = new SimulationEventBus();
        List<ScheduledSimulationEvent> published = new ArrayList<>();
        bus.subscribe(published::add);
        SimulationClock clock = new SimulationClock(CONFIGURATION, SimulationState.initial(CONFIGURATION), bus);

        List<ScheduledSimulationEvent> executed = clock.advance(CONFIGURATION.ticksPerYear());

        assertEquals(executed, published);
        assertTrue(executed.size() > 4);
        List<SimulationEventType> finalBoundaryTypes = executed.stream()
                .filter(event -> event.scheduledSimulationTick() == CONFIGURATION.ticksPerYear())
                .map(ScheduledSimulationEvent::eventType)
                .toList();
        assertEquals(List.of(
                SimulationEventType.DAILY_ROLLOVER,
                SimulationEventType.WEEKLY_ROLLOVER,
                SimulationEventType.MONTHLY_ROLLOVER,
                SimulationEventType.YEARLY_ROLLOVER
        ), finalBoundaryTypes);
    }

    @Test
    void zeroAdvanceDoesNotExecuteEvents() {
        SimulationClock clock = new SimulationClock(CONFIGURATION);

        assertEquals(List.of(), clock.advance(0L));
        assertEquals(0L, clock.simulationTick());
    }
}
