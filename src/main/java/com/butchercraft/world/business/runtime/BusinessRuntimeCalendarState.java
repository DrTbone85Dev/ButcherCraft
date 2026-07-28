package com.butchercraft.world.business.runtime;

import java.util.Objects;
import java.util.Optional;

public record BusinessRuntimeCalendarState(
        int schemaVersion,
        BusinessOperatingScheduleIdentity operatingScheduleIdentity,
        BusinessShiftSetIdentity shiftSetIdentity,
        BusinessRuntimeConfigurationIdentity configurationIdentity,
        String lastObservedWorldDayIdentity,
        boolean lastObservedOpen,
        Optional<String> lastActiveShiftIdentity,
        Optional<String> lastEvaluatedBoundary,
        String lastMovementClassification
) {
    public BusinessRuntimeCalendarState {
        if (schemaVersion != BusinessRuntimeCalendarSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported business runtime calendar state schema: " + schemaVersion);
        }
        operatingScheduleIdentity = Objects.requireNonNull(operatingScheduleIdentity, "operatingScheduleIdentity");
        shiftSetIdentity = Objects.requireNonNull(shiftSetIdentity, "shiftSetIdentity");
        configurationIdentity = Objects.requireNonNull(configurationIdentity, "configurationIdentity");
        lastObservedWorldDayIdentity = BusinessRuntimeValidation.requireText(
                lastObservedWorldDayIdentity,
                "Business runtime last observed world-day identity"
        );
        lastActiveShiftIdentity = Objects.requireNonNull(lastActiveShiftIdentity, "lastActiveShiftIdentity")
                .map(value -> BusinessRuntimeValidation.requireExternalIdentity(value, "Last active shift identity"));
        lastEvaluatedBoundary = Objects.requireNonNull(lastEvaluatedBoundary, "lastEvaluatedBoundary")
                .map(value -> BusinessRuntimeValidation.requireText(value, "Last evaluated business boundary"));
        lastMovementClassification = BusinessRuntimeValidation.requireText(
                lastMovementClassification,
                "Business runtime last movement classification"
        );
    }

    public static BusinessRuntimeCalendarState from(BusinessRuntimeObservationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Optional<String> boundary = snapshot.plantOpen()
                ? snapshot.nextClosing().map(BusinessScheduleBoundary::displayText)
                : snapshot.nextOpening().map(BusinessScheduleBoundary::displayText);
        return new BusinessRuntimeCalendarState(
                BusinessRuntimeCalendarSchema.CURRENT_VERSION,
                snapshot.operatingScheduleIdentity(),
                snapshot.shiftSetIdentity(),
                snapshot.configurationIdentity(),
                snapshot.calendar().worldDayIdentity(),
                snapshot.plantOpen(),
                snapshot.activeShift().flatMap(BusinessScheduleBoundary::identity),
                boundary,
                snapshot.movementClassification().serializedName()
        );
    }
}
