package com.butchercraft.world.simulation.time;

import java.util.Objects;

public record WorldTimeState(
        int schemaVersion,
        String configurationIdentity,
        long accumulatorRemainderNumerator,
        long lastObservedRawDayTime,
        long lastExpectedScaledDayTime,
        String sourceDimensionIdentity,
        long lastObservationGameTime,
        WorldTimeMovementClassification lastMovementClassification,
        int consecutiveUnexpectedChanges,
        boolean externalConflictDetected
) {
    public WorldTimeState {
        if (schemaVersion != WorldTimeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported world time schema version: " + schemaVersion);
        }
        configurationIdentity = requireText(configurationIdentity, "configurationIdentity");
        if (accumulatorRemainderNumerator < 0L) {
            throw new IllegalArgumentException("Accumulator remainder must not be negative: "
                    + accumulatorRemainderNumerator);
        }
        sourceDimensionIdentity = requireText(sourceDimensionIdentity, "sourceDimensionIdentity");
        if (lastObservationGameTime < 0L) {
            throw new IllegalArgumentException("Observation gameTime must not be negative: "
                    + lastObservationGameTime);
        }
        lastMovementClassification = Objects.requireNonNull(lastMovementClassification, "lastMovementClassification");
        if (consecutiveUnexpectedChanges < 0) {
            throw new IllegalArgumentException("Unexpected-change count must not be negative: "
                    + consecutiveUnexpectedChanges);
        }
    }

    public static WorldTimeState initial(
            WorldTimeConfiguration configuration,
            long observedDayTime,
            long gameTime,
            String sourceDimensionIdentity
    ) {
        Objects.requireNonNull(configuration, "configuration");
        return new WorldTimeState(
                WorldTimeSchema.CURRENT_VERSION,
                configuration.identity().value(),
                0L,
                observedDayTime,
                observedDayTime,
                sourceDimensionIdentity,
                gameTime,
                WorldTimeMovementClassification.INITIALIZED,
                0,
                false
        );
    }

    public void validate(WorldTimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (accumulatorRemainderNumerator >= configuration.scale().denominator()) {
            throw new IllegalArgumentException("Accumulator remainder exceeds configured denominator: "
                    + accumulatorRemainderNumerator);
        }
    }

    public WorldTimeState withObservation(
            long observedRawDayTime,
            long expectedScaledDayTime,
            long gameTime,
            WorldTimeMovementClassification classification,
            int unexpectedChanges,
            boolean conflictDetected
    ) {
        return new WorldTimeState(
                schemaVersion,
                configurationIdentity,
                accumulatorRemainderNumerator,
                observedRawDayTime,
                expectedScaledDayTime,
                sourceDimensionIdentity,
                gameTime,
                classification,
                unexpectedChanges,
                conflictDetected
        );
    }

    public WorldTimeState withConfigurationTransition(
            String newConfigurationIdentity,
            long normalizedRemainder,
            long observedDayTime,
            long gameTime,
            String sourceDimensionIdentity
    ) {
        return new WorldTimeState(
                schemaVersion,
                newConfigurationIdentity,
                normalizedRemainder,
                observedDayTime,
                observedDayTime,
                sourceDimensionIdentity,
                gameTime,
                WorldTimeMovementClassification.CONFIGURATION_TRANSITION,
                0,
                false
        );
    }

    public WorldTimeState restored() {
        return new WorldTimeState(
                schemaVersion,
                configurationIdentity,
                accumulatorRemainderNumerator,
                lastObservedRawDayTime,
                lastExpectedScaledDayTime,
                sourceDimensionIdentity,
                lastObservationGameTime,
                WorldTimeMovementClassification.PERSISTENCE_RESTORED,
                consecutiveUnexpectedChanges,
                externalConflictDetected
        );
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("World time state field must not be blank: " + fieldName);
        }
        return normalized;
    }
}
