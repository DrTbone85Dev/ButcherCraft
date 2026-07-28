package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;

import java.util.Objects;
import java.util.Optional;

public record BusinessScheduleBoundary(
        long businessDayIndex,
        BusinessDayOfWeek dayOfWeek,
        BusinessTimeOfDay timeOfDay,
        Optional<String> identity,
        String displayName
) {
    public BusinessScheduleBoundary {
        dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        timeOfDay = Objects.requireNonNull(timeOfDay, "timeOfDay");
        identity = Objects.requireNonNull(identity, "identity")
                .map(value -> BusinessRuntimeValidation.requireText(value, "Business boundary identity"));
        displayName = BusinessRuntimeValidation.requireText(displayName, "Business boundary display name");
    }

    public long absoluteMinute() {
        return Math.addExact(Math.multiplyExact(businessDayIndex, BusinessOperatingSchedule.MINUTES_PER_DAY),
                timeOfDay.hour() * 60L + timeOfDay.minute());
    }

    public String displayText() {
        return dayOfWeek.displayName() + " " + timeOfDay.displayText();
    }
}
