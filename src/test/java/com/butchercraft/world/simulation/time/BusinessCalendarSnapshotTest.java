package com.butchercraft.world.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessCalendarSnapshotTest {
    private static final WorldTimeConfiguration CONFIGURATION = WorldTimeConfiguration.enabled(60);
    private static final String SOURCE = "minecraft:overworld";

    @Test
    void epochDefinesMinecraftDayZeroAsMondayMorning() {
        BusinessCalendarSnapshot snapshot = snapshot(0L);

        assertEquals(0L, snapshot.businessDayIndex());
        assertEquals(BusinessDayOfWeek.MONDAY, snapshot.dayOfWeek());
        assertEquals(new BusinessTimeOfDay(6, 0), snapshot.timeOfDay());
    }

    @Test
    void businessClockTracksVisibleMinecraftClockOffset() {
        assertEquals(new BusinessTimeOfDay(12, 0), snapshot(6_000L).timeOfDay());
        assertEquals(new BusinessTimeOfDay(18, 0), snapshot(12_000L).timeOfDay());
        assertEquals(new BusinessTimeOfDay(0, 0), snapshot(18_000L).timeOfDay());
    }

    @Test
    void visibleMidnightAdvancesBusinessDayAndWeekday() {
        BusinessCalendarSnapshot snapshot = snapshot(18_000L);

        assertEquals(1L, snapshot.businessDayIndex());
        assertEquals(BusinessDayOfWeek.TUESDAY, snapshot.dayOfWeek());
    }

    @Test
    void dayOfWeekWrapsAfterSevenDays() {
        BusinessCalendarSnapshot snapshot = snapshot(7L * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS);

        assertEquals(7L, snapshot.businessDayIndex());
        assertEquals(BusinessDayOfWeek.MONDAY, snapshot.dayOfWeek());
    }

    @Test
    void worldDayIdentityBindsSourceAndBusinessDay() {
        BusinessCalendarSnapshot snapshot = snapshot(42_000L);

        assertEquals("butchercraft:world_day/v1/minecraft:overworld/2", snapshot.worldDayIdentity());
    }

    private static BusinessCalendarSnapshot snapshot(long dayTime) {
        return BusinessCalendarSnapshot.fromDayTime(dayTime, CONFIGURATION.identity(), SOURCE, 10L);
    }
}
