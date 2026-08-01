package com.butchercraft.world.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldTimeAccumulatorTest {
    @Test
    void sixtyMinuteDayAdvancesOneDayTimeUnitEveryThreeServerTicks() {
        WorldTimeAccumulator accumulator = WorldTimeAccumulator.empty(WorldTimeConfiguration.enabled(60).scale());

        WorldTimeAdvance first = accumulator.advanceOneServerTick();
        WorldTimeAdvance second = first.accumulator().advanceOneServerTick();
        WorldTimeAdvance third = second.accumulator().advanceOneServerTick();

        assertEquals(0L, first.dayTimeUnits());
        assertEquals(1L, first.accumulator().remainderNumerator());
        assertEquals(0L, second.dayTimeUnits());
        assertEquals(2L, second.accumulator().remainderNumerator());
        assertEquals(1L, third.dayTimeUnits());
        assertEquals(0L, third.accumulator().remainderNumerator());
    }

    @Test
    void sixtyMinuteDayCompletesExactlyOneMinecraftDayWithoutFloatingPointDrift() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeAccumulator accumulator = WorldTimeAccumulator.empty(configuration.scale());
        long advancedDayTime = 0L;

        for (long tick = 0L; tick < configuration.ticksPerConfiguredDay(); tick++) {
            WorldTimeAdvance advance = accumulator.advanceOneServerTick();
            advancedDayTime += advance.dayTimeUnits();
            accumulator = advance.accumulator();
        }

        assertEquals(BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS, advancedDayTime);
        assertEquals(0L, accumulator.remainderNumerator());
    }

    @Test
    void persistedRemainderResumesWithoutAddingOrLosingDayTimeUnits() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeAccumulator accumulator = WorldTimeAccumulator.empty(configuration.scale());
        accumulator = accumulator.advanceOneServerTick().accumulator();
        accumulator = accumulator.advanceOneServerTick().accumulator();
        WorldTimeAccumulator restored = new WorldTimeAccumulator(configuration.scale(), accumulator.remainderNumerator());

        WorldTimeAdvance afterRestart = restored.advanceOneServerTick();

        assertEquals(1L, afterRestart.dayTimeUnits());
        assertEquals(0L, afterRestart.accumulator().remainderNumerator());
    }

    @Test
    void invalidRemainderIsRejected() {
        WorldTimeScale scale = WorldTimeConfiguration.enabled(60).scale();

        assertThrows(IllegalArgumentException.class, () -> new WorldTimeAccumulator(scale, -1L));
        assertThrows(IllegalArgumentException.class, () -> new WorldTimeAccumulator(scale, scale.denominator()));
    }
}
