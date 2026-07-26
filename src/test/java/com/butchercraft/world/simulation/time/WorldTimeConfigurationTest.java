package com.butchercraft.world.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldTimeConfigurationTest {
    @Test
    void twentyMinuteDayProducesVanillaEquivalentRate() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(20);

        assertEquals(24_000L, configuration.ticksPerConfiguredDay());
        assertEquals(new WorldTimeScale(1L, 1L, 24_000L), configuration.scale());
    }

    @Test
    void sixtyMinuteDayProducesOneThirdRate() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);

        assertEquals(72_000L, configuration.ticksPerConfiguredDay());
        assertEquals(new WorldTimeScale(1L, 3L, 72_000L), configuration.scale());
    }

    @Test
    void arbitraryValidDurationsDeriveReducedRationalRates() {
        WorldTimeConfiguration thirty = WorldTimeConfiguration.enabled(30);
        WorldTimeConfiguration ninety = WorldTimeConfiguration.enabled(90);
        WorldTimeConfiguration twoHours = WorldTimeConfiguration.enabled(120);

        assertEquals(new WorldTimeScale(2L, 3L, 36_000L), thirty.scale());
        assertEquals(new WorldTimeScale(2L, 9L, 108_000L), ninety.scale());
        assertEquals(new WorldTimeScale(1L, 6L, 144_000L), twoHours.scale());
    }

    @Test
    void invalidDurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorldTimeConfiguration.enabled(0));
        assertThrows(IllegalArgumentException.class, () -> WorldTimeConfiguration.enabled(-1));
        assertThrows(IllegalArgumentException.class, () -> WorldTimeConfiguration.enabled(1_441));
    }

    @Test
    void configurationIdentityIsDeterministicAndSensitiveToAuthoritativeValues() {
        WorldTimeConfiguration first = WorldTimeConfiguration.enabled(60);
        WorldTimeConfiguration equivalent = WorldTimeConfiguration.enabled(60);
        WorldTimeConfiguration disabled = WorldTimeConfiguration.disabled(60);
        WorldTimeConfiguration shorter = WorldTimeConfiguration.enabled(30);

        assertEquals(first.identity(), equivalent.identity());
        assertNotEquals(first.identity(), disabled.identity());
        assertNotEquals(first.identity(), shorter.identity());
    }
}
