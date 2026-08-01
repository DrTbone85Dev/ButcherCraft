package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessOperatingScheduleTest {
    private static final WorldTimeConfiguration CONFIGURATION = WorldTimeConfiguration.enabled(60);
    private static final String SOURCE = "minecraft:overworld";

    @Test
    void defaultScheduleUsesInclusiveOpeningAndExclusiveClosing() {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.defaultSchedule();

        assertTrue(schedule.isOpen(snapshot(0L, 6, 0)));
        assertTrue(schedule.isOpen(snapshot(0L, 17, 59)));
        assertFalse(schedule.isOpen(snapshot(0L, 5, 59)));
        assertFalse(schedule.isOpen(snapshot(0L, 18, 0)));
    }

    @Test
    void closedDaysRemainClosed() {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.defaultSchedule();

        assertFalse(schedule.isOpen(snapshot(5L, 12, 0)));
        assertTrue(schedule.nextOpening(snapshot(5L, 12, 0)).isPresent());
        assertEquals(7L, schedule.nextOpening(snapshot(5L, 12, 0)).orElseThrow().businessDayIndex());
        assertEquals(new BusinessTimeOfDay(6, 0),
                schedule.nextOpening(snapshot(5L, 12, 0)).orElseThrow().timeOfDay());
    }

    @Test
    void overnightWindowSpansBusinessDayBoundary() {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.builder()
                .open(BusinessDayOfWeek.MONDAY, "18:00-02:00")
                .build();

        assertFalse(schedule.isOpen(snapshot(0L, 17, 59)));
        assertTrue(schedule.isOpen(snapshot(0L, 18, 0)));
        assertTrue(schedule.isOpen(snapshot(1L, 1, 59)));
        assertFalse(schedule.isOpen(snapshot(1L, 2, 0)));
    }

    @Test
    void nextOpeningAndClosingAreDeterministicAcrossWeekWrap() {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.defaultSchedule();
        Optional<BusinessScheduleBoundary> opening = schedule.nextOpening(snapshot(4L, 19, 0));
        Optional<BusinessScheduleBoundary> closing = schedule.nextClosing(snapshot(0L, 6, 0));

        assertTrue(opening.isPresent());
        assertEquals(7L, opening.orElseThrow().businessDayIndex());
        assertEquals(BusinessDayOfWeek.MONDAY, opening.orElseThrow().dayOfWeek());
        assertEquals(new BusinessTimeOfDay(6, 0), opening.orElseThrow().timeOfDay());
        assertTrue(closing.isPresent());
        assertEquals(new BusinessTimeOfDay(18, 0), closing.orElseThrow().timeOfDay());
    }

    @Test
    void invalidIntervalsAndOverlapsFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () ->
                BusinessOperatingSchedule.builder()
                        .open(BusinessDayOfWeek.MONDAY, "06:00-24:00")
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                BusinessOperatingSchedule.builder()
                        .open(BusinessDayOfWeek.MONDAY, "18:00-02:00")
                        .open(BusinessDayOfWeek.TUESDAY, "01:00-03:00")
                        .build());
    }

    static BusinessCalendarSnapshot snapshot(long dayIndex, int hour, int minute) {
        long minuteOfDay = hour * 60L + minute;
        long dayTimeOfDay = minuteOfDay * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                / BusinessCalendarSnapshot.BUSINESS_MINUTES_PER_DAY;
        long observedDayTime = dayIndex * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                + dayTimeOfDay - BusinessCalendarSnapshot.MINECRAFT_VISIBLE_MIDNIGHT_OFFSET;
        return new BusinessCalendarSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                dayIndex,
                BusinessDayOfWeek.fromDayIndex(dayIndex),
                new BusinessTimeOfDay(hour, minute),
                dayTimeOfDay,
                dayTimeOfDay,
                BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS,
                "butchercraft:world_day/v1/" + SOURCE + "/" + dayIndex,
                CONFIGURATION.identity(),
                SOURCE,
                Math.max(0L, dayIndex),
                observedDayTime
        );
    }
}
