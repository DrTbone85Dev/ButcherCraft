package com.butchercraft.world.simulation.time;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public final class WorldTimeController {
    public static final int CONFLICT_OBSERVATION_THRESHOLD = 3;

    public WorldTimeTickResult advance(
            WorldTimeState state,
            WorldTimeConfiguration configuration,
            long gameTime,
            long observedDayTime,
            String sourceDimensionIdentity
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(configuration, "configuration");
        sourceDimensionIdentity = requireSourceDimension(sourceDimensionIdentity);
        List<WorldTimeFailure> failures = new ArrayList<>();

        if (gameTime < 0L) {
            failures.add(WorldTimeFailure.of(WorldTimeFailureCode.INVALID_OBSERVATION,
                    "gameTime must not be negative: " + gameTime));
            return WorldTimeTickResult.failure(state, snapshot(state, configuration, gameTime, observedDayTime),
                    failures);
        }
        if (!state.sourceDimensionIdentity().equals(sourceDimensionIdentity)) {
            failures.add(WorldTimeFailure.of(WorldTimeFailureCode.WORLD_IDENTITY_MISMATCH,
                    "World time source dimension changed from " + state.sourceDimensionIdentity()
                            + " to " + sourceDimensionIdentity));
            return WorldTimeTickResult.failure(state, snapshot(state, configuration, gameTime, observedDayTime),
                    failures);
        }
        if (state.lastObservationGameTime() == gameTime) {
            WorldTimeState next = state.withObservation(
                    observedDayTime,
                    observedDayTime,
                    gameTime,
                    WorldTimeMovementClassification.DUPLICATE_TICK_APPLICATION,
                    state.consecutiveUnexpectedChanges(),
                    state.externalConflictDetected()
            );
            failures.add(WorldTimeFailure.of(WorldTimeFailureCode.DUPLICATE_TICK_APPLICATION,
                    "World time tick already observed gameTime " + gameTime));
            return WorldTimeTickResult.failure(next, snapshot(next, configuration, gameTime, observedDayTime),
                    failures);
        }
        if (state.lastObservationGameTime() > gameTime) {
            WorldTimeState next = state.withObservation(
                    observedDayTime,
                    observedDayTime,
                    gameTime,
                    WorldTimeMovementClassification.BACKWARD_JUMP,
                    Math.addExact(state.consecutiveUnexpectedChanges(), 1),
                    state.externalConflictDetected()
            );
            failures.add(WorldTimeFailure.of(WorldTimeFailureCode.BACKWARD_TIME_MOVEMENT,
                    "gameTime moved backward from " + state.lastObservationGameTime() + " to " + gameTime));
            return WorldTimeTickResult.failure(next, snapshot(next, configuration, gameTime, observedDayTime),
                    failures);
        }

        WorldTimeConfigurationIdentity currentIdentity = configuration.identity();
        if (!state.configurationIdentity().equals(currentIdentity.value())) {
            WorldTimeAccumulator normalized = new WorldTimeAccumulator(
                    configuration.scale(),
                    Math.floorMod(state.accumulatorRemainderNumerator(), configuration.scale().denominator())
            );
            WorldTimeState next = state.withConfigurationTransition(
                    currentIdentity.value(),
                    normalized.remainderNumerator(),
                    observedDayTime,
                    gameTime,
                    sourceDimensionIdentity
            );
            return WorldTimeTickResult.success(
                    next,
                    snapshot(next, configuration, gameTime, observedDayTime),
                    OptionalLong.empty(),
                    true
            );
        }

        if (!configuration.enabled()) {
            WorldTimeState next = state.withObservation(
                    observedDayTime,
                    observedDayTime,
                    gameTime,
                    WorldTimeMovementClassification.DISABLED_VANILLA_CONTROL,
                    0,
                    false
            );
            return WorldTimeTickResult.success(next, snapshot(next, configuration, gameTime, observedDayTime),
                    OptionalLong.empty(), true);
        }

        if (observedDayTime != state.lastExpectedScaledDayTime()) {
            boolean forward = observedDayTime > state.lastExpectedScaledDayTime();
            int unexpectedChanges = Math.addExact(state.consecutiveUnexpectedChanges(), 1);
            boolean conflict = state.externalConflictDetected()
                    || unexpectedChanges >= CONFLICT_OBSERVATION_THRESHOLD;
            WorldTimeMovementClassification classification = conflict
                    ? WorldTimeMovementClassification.EXTERNAL_AUTHORITY_CONFLICT
                    : forward
                    ? WorldTimeMovementClassification.FORWARD_JUMP
                    : WorldTimeMovementClassification.BACKWARD_JUMP;
            WorldTimeState next = state.withObservation(
                    observedDayTime,
                    observedDayTime,
                    gameTime,
                    classification,
                    unexpectedChanges,
                    conflict
            );
            if (!forward) {
                failures.add(WorldTimeFailure.of(WorldTimeFailureCode.BACKWARD_TIME_MOVEMENT,
                        "dayTime moved backward from expected " + state.lastExpectedScaledDayTime()
                                + " to observed " + observedDayTime));
            }
            if (conflict) {
                failures.add(WorldTimeFailure.of(WorldTimeFailureCode.EXTERNAL_TIME_CONTROLLER_CONFLICT,
                        "Repeated unexpected dayTime changes indicate another time authority"));
            }
            return new WorldTimeTickResult(next, snapshot(next, configuration, gameTime, observedDayTime),
                    OptionalLong.empty(), List.copyOf(failures), true);
        }

        WorldTimeAccumulator accumulator = new WorldTimeAccumulator(
                configuration.scale(),
                state.accumulatorRemainderNumerator()
        );
        WorldTimeAdvance advance = accumulator.advanceOneServerTick();
        long nextDayTime;
        try {
            nextDayTime = Math.addExact(observedDayTime, advance.dayTimeUnits());
        } catch (ArithmeticException exception) {
            failures.add(WorldTimeFailure.of(WorldTimeFailureCode.ARITHMETIC_OVERFLOW,
                    "dayTime advancement overflowed at " + observedDayTime));
            return WorldTimeTickResult.failure(state, snapshot(state, configuration, gameTime, observedDayTime),
                    failures);
        }
        WorldTimeState next = new WorldTimeState(
                WorldTimeSchema.CURRENT_VERSION,
                currentIdentity.value(),
                advance.accumulator().remainderNumerator(),
                observedDayTime,
                nextDayTime,
                sourceDimensionIdentity,
                gameTime,
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT,
                0,
                false
        );
        OptionalLong publish = advance.dayTimeUnits() == 0L ? OptionalLong.empty() : OptionalLong.of(nextDayTime);
        return WorldTimeTickResult.success(next, snapshot(next, configuration, gameTime, nextDayTime), publish,
                publish.isPresent());
    }

    public WorldTimeStatusSnapshot snapshot(
            WorldTimeState state,
            WorldTimeConfiguration configuration,
            long gameTime,
            long observedDayTime
    ) {
        BusinessCalendarSnapshot calendar = BusinessCalendarSnapshot.fromDayTime(
                observedDayTime,
                configuration.identity(),
                state.sourceDimensionIdentity(),
                gameTime
        );
        return new WorldTimeStatusSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                configuration.enabled(),
                configuration.dayLengthMinutes(),
                configuration.scale().numerator(),
                configuration.scale().denominator(),
                configuration.identity(),
                state.sourceDimensionIdentity(),
                gameTime,
                observedDayTime,
                calendar,
                state.accumulatorRemainderNumerator(),
                state.lastMovementClassification(),
                state.externalConflictDetected()
        );
    }

    public static WorldTimeState initialState(
            WorldTimeConfiguration configuration,
            long observedDayTime,
            long gameTime,
            String sourceDimensionIdentity
    ) {
        return WorldTimeState.initial(configuration, observedDayTime, gameTime,
                requireSourceDimension(sourceDimensionIdentity));
    }

    private static String requireSourceDimension(String sourceDimensionIdentity) {
        String normalized = Objects.requireNonNull(sourceDimensionIdentity, "sourceDimensionIdentity").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Source dimension identity must not be blank");
        }
        return normalized;
    }
}
