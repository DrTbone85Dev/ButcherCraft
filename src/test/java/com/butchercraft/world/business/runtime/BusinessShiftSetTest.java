package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessShiftSetTest {
    @Test
    void activeShiftUsesInclusiveStartAndExclusiveEnd() {
        BusinessShiftSet shifts = BusinessShiftSet.defaultShifts(BusinessOperatingSchedule.defaultSchedule());

        assertEquals("Day Shift", shifts.activeShift(
                BusinessOperatingScheduleTest.snapshot(0L, 6, 0)).orElseThrow().displayName());
        assertEquals("Day Shift", shifts.activeShift(
                BusinessOperatingScheduleTest.snapshot(0L, 14, 29)).orElseThrow().displayName());
        assertEquals("Evening Shift", shifts.activeShift(
                BusinessOperatingScheduleTest.snapshot(0L, 14, 30)).orElseThrow().displayName());
        assertFalse(shifts.activeShift(BusinessOperatingScheduleTest.snapshot(0L, 18, 0)).isPresent());
    }

    @Test
    void nextShiftWrapsAcrossDaysAndWeeks() {
        BusinessShiftSet shifts = BusinessShiftSet.defaultShifts(BusinessOperatingSchedule.defaultSchedule());

        BusinessScheduleBoundary nextToday = shifts.nextShift(
                BusinessOperatingScheduleTest.snapshot(0L, 5, 30)).orElseThrow();
        BusinessScheduleBoundary nextWeek = shifts.nextShift(
                BusinessOperatingScheduleTest.snapshot(4L, 19, 0)).orElseThrow();

        assertEquals(0L, nextToday.businessDayIndex());
        assertEquals(new BusinessTimeOfDay(6, 0), nextToday.timeOfDay());
        assertEquals("Day Shift", nextToday.displayName());
        assertEquals(7L, nextWeek.businessDayIndex());
        assertEquals(BusinessDayOfWeek.MONDAY, nextWeek.dayOfWeek());
        assertEquals("Day Shift", nextWeek.displayName());
    }

    @Test
    void duplicateShiftIdsAndOverlapsAreRejected() {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.defaultSchedule();
        BusinessShiftDefinition first = shift("duplicate", "First", "06:00", "08:00");
        BusinessShiftDefinition second = shift("duplicate", "Second", "08:00", "10:00");
        BusinessShiftDefinition overlapping = shift("overlap", "Overlap", "07:00", "09:00");

        assertThrows(IllegalArgumentException.class, () -> BusinessShiftSet.of(List.of(first, second), schedule));
        assertThrows(IllegalArgumentException.class, () -> BusinessShiftSet.of(List.of(first, overlapping), schedule));
    }

    @Test
    void shiftsOutsideOperatingHoursAreRejected() {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.defaultSchedule();

        assertThrows(IllegalArgumentException.class, () ->
                BusinessShiftSet.of(List.of(shift("early", "Early", "05:00", "06:00")), schedule));
        assertThrows(IllegalArgumentException.class, () ->
                BusinessShiftSet.of(List.of(shift("late", "Late", "17:00", "19:00")), schedule));
    }

    @Test
    void deterministicOrderingProducesStableIdentity() {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.defaultSchedule();
        BusinessShiftDefinition day = shift("day_shift", "Day Shift", "06:00", "14:30");
        BusinessShiftDefinition evening = shift("evening_shift", "Evening Shift", "14:30", "18:00");

        BusinessShiftSet ordered = BusinessShiftSet.of(List.of(day, evening), schedule);
        BusinessShiftSet reversed = BusinessShiftSet.of(List.of(evening, day), schedule);

        assertEquals(ordered.identity(), reversed.identity());
        assertEquals(List.of("day_shift", "evening_shift"),
                ordered.shifts().stream().map(BusinessShiftDefinition::id).toList());
        assertTrue(ordered.canonical().contains("ordering=canonical_shift_id"));
    }

    private static BusinessShiftDefinition shift(String id, String name, String start, String end) {
        return BusinessShiftDefinition.of(id, name, start, end, Set.of(BusinessDayOfWeek.MONDAY));
    }
}
