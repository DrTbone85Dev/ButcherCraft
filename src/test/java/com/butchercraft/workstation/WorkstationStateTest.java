package com.butchercraft.workstation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationStateTest {
    @Test
    void validStateTransitionsAreAllowed() {
        assertTrue(WorkstationState.IDLE.canTransitionTo(WorkstationState.READY));
        assertTrue(WorkstationState.READY.canTransitionTo(WorkstationState.PROCESSING));
        assertTrue(WorkstationState.PROCESSING.canTransitionTo(WorkstationState.COMPLETE));
        assertTrue(WorkstationState.PROCESSING.canTransitionTo(WorkstationState.BLOCKED));
        assertTrue(WorkstationState.COMPLETE.canTransitionTo(WorkstationState.IDLE));
        assertTrue(WorkstationState.ERROR.canTransitionTo(WorkstationState.IDLE));
    }

    @Test
    void invalidStateTransitionsAreRejected() {
        assertFalse(WorkstationState.IDLE.canTransitionTo(WorkstationState.COMPLETE));
        assertThrows(IllegalStateException.class, () -> WorkstationState.COMPLETE.transitionTo(WorkstationState.PROCESSING));
        assertThrows(IllegalStateException.class, () -> WorkstationState.ERROR.transitionTo(WorkstationState.PROCESSING));
    }
}
