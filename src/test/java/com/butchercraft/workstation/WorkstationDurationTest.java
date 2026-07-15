package com.butchercraft.workstation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkstationDurationTest {
    @Test
    void threeSecondsConvertsToSixtyTicks() {
        assertEquals(60, WorkstationDuration.millisecondsToTicks(3_000));
    }

    @Test
    void subTickDurationsRoundUpToOneTick() {
        assertEquals(1, WorkstationDuration.millisecondsToTicks(1));
        assertEquals(1, WorkstationDuration.millisecondsToTicks(50));
        assertEquals(2, WorkstationDuration.millisecondsToTicks(51));
    }

    @Test
    void zeroAndNegativeDurationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorkstationDuration.millisecondsToTicks(0));
        assertThrows(IllegalArgumentException.class, () -> WorkstationDuration.millisecondsToTicks(-1));
    }

    @Test
    void overflowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorkstationDuration.millisecondsToTicks(Long.MAX_VALUE));
    }
}
