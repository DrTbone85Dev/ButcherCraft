package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.WorldTimeConfigurationIdentity;

import java.util.Objects;

public record BusinessRuntimeCalendarConfiguration(
        boolean enabled,
        String timezoneMode,
        BusinessOperatingSchedule operatingSchedule,
        BusinessShiftSet shiftSet,
        boolean productionOrderDeadlinesEnabled,
        int productionOrderDefaultDeadlineMinutes,
        BusinessRuntimeConfigurationIdentity identity
) {
    public BusinessRuntimeCalendarConfiguration(
            boolean enabled,
            String timezoneMode,
            BusinessOperatingSchedule operatingSchedule,
            BusinessShiftSet shiftSet,
            boolean productionOrderDeadlinesEnabled,
            int productionOrderDefaultDeadlineMinutes,
            WorldTimeConfigurationIdentity worldTimeConfigurationIdentity
    ) {
        this(
                enabled,
                BusinessRuntimeValidation.requireText(timezoneMode, "Business runtime timezone mode"),
                Objects.requireNonNull(operatingSchedule, "operatingSchedule"),
                Objects.requireNonNull(shiftSet, "shiftSet"),
                productionOrderDeadlinesEnabled,
                productionOrderDefaultDeadlineMinutes,
                BusinessRuntimeConfigurationIdentity.from(
                        operatingSchedule,
                        shiftSet,
                        worldTimeConfigurationIdentity,
                        enabled,
                        productionOrderDeadlinesEnabled,
                        productionOrderDefaultDeadlineMinutes
                )
        );
    }

    public BusinessRuntimeCalendarConfiguration {
        timezoneMode = BusinessRuntimeValidation.requireText(timezoneMode, "Business runtime timezone mode");
        if (!BusinessRuntimeCalendarSchema.TIMEZONE_MODE_BUSINESS_CALENDAR.equals(timezoneMode)) {
            throw new IllegalArgumentException("Unsupported business runtime timezone mode: " + timezoneMode);
        }
        operatingSchedule = Objects.requireNonNull(operatingSchedule, "operatingSchedule");
        shiftSet = Objects.requireNonNull(shiftSet, "shiftSet");
        if (productionOrderDefaultDeadlineMinutes < 0) {
            throw new IllegalArgumentException("Production Order default deadline minutes must not be negative");
        }
        identity = Objects.requireNonNull(identity, "identity");
    }

    public static BusinessRuntimeCalendarConfiguration defaults(WorldTimeConfigurationIdentity worldTimeIdentity) {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.defaultSchedule();
        return new BusinessRuntimeCalendarConfiguration(
                true,
                BusinessRuntimeCalendarSchema.TIMEZONE_MODE_BUSINESS_CALENDAR,
                schedule,
                BusinessShiftSet.defaultShifts(schedule),
                true,
                240,
                worldTimeIdentity
        );
    }
}
