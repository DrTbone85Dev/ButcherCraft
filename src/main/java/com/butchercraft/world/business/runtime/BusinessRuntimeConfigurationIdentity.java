package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.WorldTimeConfigurationIdentity;

import java.util.Objects;

public record BusinessRuntimeConfigurationIdentity(String value) {
    public BusinessRuntimeConfigurationIdentity {
        value = BusinessRuntimeValidation.requireExternalIdentity(value, "Business runtime configuration identity");
    }

    public static BusinessRuntimeConfigurationIdentity from(
            BusinessOperatingSchedule schedule,
            BusinessShiftSet shiftSet,
            WorldTimeConfigurationIdentity worldTimeConfigurationIdentity,
            boolean enabled,
            boolean productionOrderDeadlinesEnabled,
            int productionOrderDefaultDeadlineMinutes
    ) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(shiftSet, "shiftSet");
        Objects.requireNonNull(worldTimeConfigurationIdentity, "worldTimeConfigurationIdentity");
        if (productionOrderDefaultDeadlineMinutes < 0) {
            throw new IllegalArgumentException("Production Order deadline minutes must not be negative");
        }
        String canonical = "schema_version=" + BusinessRuntimeCalendarSchema.CURRENT_VERSION + "\n"
                + "enabled=" + enabled + "\n"
                + "timezone_mode=" + BusinessRuntimeCalendarSchema.TIMEZONE_MODE_BUSINESS_CALENDAR + "\n"
                + "world_time_configuration_identity=" + worldTimeConfigurationIdentity.value() + "\n"
                + "operating_schedule_identity=" + schedule.identity().value() + "\n"
                + "shift_set_identity=" + shiftSet.identity().value() + "\n"
                + "production_order_deadlines_enabled=" + productionOrderDeadlinesEnabled + "\n"
                + "production_order_default_deadline_minutes=" + productionOrderDefaultDeadlineMinutes + "\n";
        return new BusinessRuntimeConfigurationIdentity("butchercraft:business_runtime_config/v1/"
                + BusinessRuntimeDigest.sha256(canonical));
    }
}
