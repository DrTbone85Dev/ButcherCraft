package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;

import java.util.Objects;
import java.util.Optional;

public record BusinessRuntimeObservationSnapshot(
        int schemaVersion,
        boolean enabled,
        BusinessCalendarSnapshot calendar,
        boolean plantOpen,
        Optional<BusinessScheduleBoundary> currentOperatingWindow,
        Optional<BusinessScheduleBoundary> nextOpening,
        Optional<BusinessScheduleBoundary> nextClosing,
        Optional<BusinessScheduleBoundary> activeShift,
        Optional<BusinessScheduleBoundary> nextShift,
        BusinessOperatingScheduleIdentity operatingScheduleIdentity,
        BusinessShiftSetIdentity shiftSetIdentity,
        BusinessRuntimeConfigurationIdentity configurationIdentity,
        WorldTimeMovementClassification movementClassification
) {
    public BusinessRuntimeObservationSnapshot {
        if (schemaVersion != BusinessRuntimeCalendarSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported business runtime observation schema: " + schemaVersion);
        }
        calendar = Objects.requireNonNull(calendar, "calendar");
        currentOperatingWindow = Objects.requireNonNull(currentOperatingWindow, "currentOperatingWindow");
        nextOpening = Objects.requireNonNull(nextOpening, "nextOpening");
        nextClosing = Objects.requireNonNull(nextClosing, "nextClosing");
        activeShift = Objects.requireNonNull(activeShift, "activeShift");
        nextShift = Objects.requireNonNull(nextShift, "nextShift");
        operatingScheduleIdentity = Objects.requireNonNull(operatingScheduleIdentity, "operatingScheduleIdentity");
        shiftSetIdentity = Objects.requireNonNull(shiftSetIdentity, "shiftSetIdentity");
        configurationIdentity = Objects.requireNonNull(configurationIdentity, "configurationIdentity");
        movementClassification = Objects.requireNonNull(movementClassification, "movementClassification");
        if (plantOpen != currentOperatingWindow.isPresent()) {
            throw new IllegalArgumentException("Plant-open flag must match the current operating window");
        }
    }

    public static BusinessRuntimeObservationSnapshot observe(
            BusinessCalendarSnapshot calendar,
            BusinessRuntimeCalendarConfiguration configuration,
            WorldTimeMovementClassification movementClassification
    ) {
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(configuration, "configuration");
        if (!configuration.enabled()) {
            return new BusinessRuntimeObservationSnapshot(
                    BusinessRuntimeCalendarSchema.CURRENT_VERSION,
                    false,
                    calendar,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    configuration.operatingSchedule().identity(),
                    configuration.shiftSet().identity(),
                    configuration.identity(),
                    movementClassification
            );
        }
        Optional<BusinessScheduleBoundary> currentWindow = configuration.operatingSchedule().currentWindow(calendar);
        return new BusinessRuntimeObservationSnapshot(
                BusinessRuntimeCalendarSchema.CURRENT_VERSION,
                true,
                calendar,
                currentWindow.isPresent(),
                currentWindow,
                configuration.operatingSchedule().nextOpening(calendar),
                configuration.operatingSchedule().nextClosing(calendar),
                configuration.shiftSet().activeShift(calendar),
                configuration.shiftSet().nextShift(calendar),
                configuration.operatingSchedule().identity(),
                configuration.shiftSet().identity(),
                configuration.identity(),
                movementClassification
        );
    }

    public String businessTimeDisplay() {
        return calendar.dayOfWeek().displayName() + " " + calendar.timeOfDay().displayText();
    }
}
