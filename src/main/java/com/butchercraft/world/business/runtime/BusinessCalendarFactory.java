package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;

final class BusinessCalendarFactory {
    private BusinessCalendarFactory() {
    }

    static BusinessCalendarSnapshot synthetic(long absoluteMinute) {
        long day = Math.floorDiv(absoluteMinute, BusinessOperatingSchedule.MINUTES_PER_DAY);
        int minute = (int) Math.floorMod(absoluteMinute, BusinessOperatingSchedule.MINUTES_PER_DAY);
        return new BusinessCalendarSnapshot(
                com.butchercraft.world.simulation.time.WorldTimeSchema.CURRENT_VERSION,
                day,
                BusinessDayOfWeek.fromDayIndex(day),
                new BusinessTimeOfDay(minute / 60, minute % 60),
                0L,
                0L,
                BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS,
                "butchercraft:world_day/v1/minecraft:overworld/" + day,
                WorldTimeConfiguration.defaults().identity(),
                "minecraft:overworld",
                0L,
                0L
        );
    }
}
