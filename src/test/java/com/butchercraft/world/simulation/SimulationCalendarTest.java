package com.butchercraft.world.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationCalendarTest {
    private static final SimulationConfiguration CONFIGURATION =
            new SimulationConfiguration(1, 2, 3, 4, 2, 8);

    @Test
    void calendarIsDerivedFromSimulationTick() {
        long tick = CONFIGURATION.ticksPerMonth() * 3L
                + CONFIGURATION.ticksPerWeek()
                + CONFIGURATION.ticksPerDay() * 2L;

        SimulationCalendar calendar = SimulationCalendar.fromTick(tick, CONFIGURATION);

        assertEquals(7, calendar.day());
        assertEquals(2, calendar.week());
        assertEquals(4, calendar.month());
        assertEquals(1, calendar.year());
        assertEquals(3, calendar.weekday());
        assertEquals(Season.SUMMER, calendar.season());
    }

    @Test
    void timeIsDerivedFromSimulationTick() {
        SimulationTime time = SimulationTime.fromTick(5L, CONFIGURATION);

        assertEquals(5L, time.simulationTick());
        assertEquals(5L, time.elapsedSimulationMinutes());
        assertEquals(2, time.hour());
        assertEquals(1, time.minute());
    }

    @Test
    void invalidConfigurationAndCalendarValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationConfiguration(0, 60, 24, 7, 4, 12));
        assertThrows(IllegalArgumentException.class, () -> new SimulationCalendar(0, 1, 1, 1, 1, Season.SPRING));
        assertThrows(IllegalArgumentException.class, () -> new SimulationTime(-1L, 0L, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new SimulationCalendar(9, 1, 1, 1, 1, Season.SPRING).validate(CONFIGURATION));
    }

    @Test
    void negativeTickIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SimulationCalendar.fromTick(-1L, CONFIGURATION));
        assertThrows(IllegalArgumentException.class, () -> SimulationTime.fromTick(-1L, CONFIGURATION));
    }
}
