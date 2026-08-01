package com.butchercraft.world.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTimeControllerTest {
    private static final String SOURCE = "minecraft:overworld";

    private final WorldTimeController controller = new WorldTimeController();

    @Test
    void normalScaledAdvancementUsesAccumulatorAndPublishesOnlyWholeDayTimeUnits() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeState state = WorldTimeController.initialState(configuration, 0L, 0L, SOURCE);

        WorldTimeTickResult first = controller.advance(state, configuration, 1L, 0L, SOURCE);
        WorldTimeTickResult second = controller.advance(first.state(), configuration, 2L, 0L, SOURCE);
        WorldTimeTickResult third = controller.advance(second.state(), configuration, 3L, 0L, SOURCE);

        assertTrue(first.dayTimeToPublish().isEmpty());
        assertTrue(second.dayTimeToPublish().isEmpty());
        assertEquals(1L, third.dayTimeToPublish().orElseThrow());
        assertEquals(0L, third.state().accumulatorRemainderNumerator());
        assertEquals(WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT,
                third.state().lastMovementClassification());
    }

    @Test
    void forwardJumpUpdatesCalendarDirectlyWithoutCatchUpPublish() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeState state = WorldTimeController.initialState(configuration, 0L, 0L, SOURCE);

        WorldTimeTickResult result = controller.advance(state, configuration, 1L, 48_000L, SOURCE);

        assertTrue(result.dayTimeToPublish().isEmpty());
        assertEquals(WorldTimeMovementClassification.FORWARD_JUMP, result.state().lastMovementClassification());
        assertEquals(48_000L, result.state().lastExpectedScaledDayTime());
        assertEquals(2L, result.snapshot().businessCalendar().businessDayIndex());
    }

    @Test
    void backwardJumpIsExplicitAndDoesNotDuplicateDayBoundaryWork() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeState state = WorldTimeController.initialState(configuration, 1_000L, 0L, SOURCE);

        WorldTimeTickResult result = controller.advance(state, configuration, 1L, 500L, SOURCE);

        assertFalse(result.successful());
        assertTrue(result.dayTimeToPublish().isEmpty());
        assertEquals(WorldTimeMovementClassification.BACKWARD_JUMP, result.state().lastMovementClassification());
        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == WorldTimeFailureCode.BACKWARD_TIME_MOVEMENT));
    }

    @Test
    void repeatedUnexpectedMovementBecomesExternalAuthorityConflict() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeState state = WorldTimeController.initialState(configuration, 0L, 0L, SOURCE);

        WorldTimeTickResult first = controller.advance(state, configuration, 1L, 10L, SOURCE);
        WorldTimeTickResult second = controller.advance(first.state(), configuration, 2L, 20L, SOURCE);
        WorldTimeTickResult third = controller.advance(second.state(), configuration, 3L, 30L, SOURCE);

        assertEquals(WorldTimeMovementClassification.EXTERNAL_AUTHORITY_CONFLICT,
                third.state().lastMovementClassification());
        assertTrue(third.state().externalConflictDetected());
        assertTrue(third.failures().stream()
                .anyMatch(failure -> failure.code() == WorldTimeFailureCode.EXTERNAL_TIME_CONTROLLER_CONFLICT));
    }

    @Test
    void configurationChangePreservesCurrentSunPositionAndAppliesProspectively() {
        WorldTimeConfiguration original = WorldTimeConfiguration.enabled(60);
        WorldTimeConfiguration updated = WorldTimeConfiguration.enabled(30);
        WorldTimeState state = new WorldTimeState(
                WorldTimeSchema.CURRENT_VERSION,
                original.identity().value(),
                2L,
                1_234L,
                1_234L,
                SOURCE,
                7L,
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT,
                0,
                false
        );

        WorldTimeTickResult result = controller.advance(state, updated, 8L, 1_234L, SOURCE);

        assertTrue(result.dayTimeToPublish().isEmpty());
        assertEquals(updated.identity().value(), result.state().configurationIdentity());
        assertEquals(1_234L, result.state().lastExpectedScaledDayTime());
        assertEquals(WorldTimeMovementClassification.CONFIGURATION_TRANSITION,
                result.state().lastMovementClassification());
    }

    @Test
    void disabledModeObservesVanillaWithoutPublishingScaledTime() {
        WorldTimeConfiguration enabled = WorldTimeConfiguration.enabled(60);
        WorldTimeConfiguration disabled = WorldTimeConfiguration.disabled(60);
        WorldTimeState state = WorldTimeController.initialState(enabled, 0L, 0L, SOURCE);

        WorldTimeTickResult transition = controller.advance(state, disabled, 1L, 1L, SOURCE);
        WorldTimeTickResult result = controller.advance(transition.state(), disabled, 2L, 2L, SOURCE);

        assertTrue(result.dayTimeToPublish().isEmpty());
        assertEquals(WorldTimeMovementClassification.DISABLED_VANILLA_CONTROL,
                result.state().lastMovementClassification());
    }

    @Test
    void duplicateTickApplicationFailsExplicitly() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeState state = WorldTimeController.initialState(configuration, 0L, 10L, SOURCE);

        WorldTimeTickResult result = controller.advance(state, configuration, 10L, 0L, SOURCE);

        assertFalse(result.successful());
        assertEquals(WorldTimeMovementClassification.DUPLICATE_TICK_APPLICATION,
                result.state().lastMovementClassification());
        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == WorldTimeFailureCode.DUPLICATE_TICK_APPLICATION));
    }

    @Test
    void sourceDimensionChangeIsRejected() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        WorldTimeState state = WorldTimeController.initialState(configuration, 0L, 0L, SOURCE);

        WorldTimeTickResult result = controller.advance(state, configuration, 1L, 0L, "minecraft:the_nether");

        assertFalse(result.successful());
        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.code() == WorldTimeFailureCode.WORLD_IDENTITY_MISMATCH));
    }
}
