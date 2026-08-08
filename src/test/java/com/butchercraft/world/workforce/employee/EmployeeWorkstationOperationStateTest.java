package com.butchercraft.world.workforce.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeWorkstationOperationStateTest {
    @Test
    void operationLifecycleIsBoundedAndTerminalStatesReturnOnlyToIdle() {
        assertTrue(EmployeeWorkstationOperationState.IDLE.canTransitionTo(
                EmployeeWorkstationOperationState.PREPARING));
        assertTrue(EmployeeWorkstationOperationState.PREPARING.canTransitionTo(
                EmployeeWorkstationOperationState.OPERATING));
        assertTrue(EmployeeWorkstationOperationState.OPERATING.canTransitionTo(
                EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION));
        assertTrue(EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION.canTransitionTo(
                EmployeeWorkstationOperationState.OPERATION_COMPLETE));
        assertTrue(EmployeeWorkstationOperationState.OPERATION_COMPLETE.canTransitionTo(
                EmployeeWorkstationOperationState.IDLE));
        assertTrue(EmployeeWorkstationOperationState.FAILURE.canTransitionTo(
                EmployeeWorkstationOperationState.IDLE));

        assertFalse(EmployeeWorkstationOperationState.IDLE.canTransitionTo(
                EmployeeWorkstationOperationState.OPERATING));
        assertFalse(EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION.canTransitionTo(
                EmployeeWorkstationOperationState.PREPARING));
        assertFalse(EmployeeWorkstationOperationState.OPERATION_COMPLETE.canTransitionTo(
                EmployeeWorkstationOperationState.PREPARING));
        assertFalse(EmployeeWorkstationOperationState.FAILURE.canTransitionTo(
                EmployeeWorkstationOperationState.PREPARING));
    }

    @Test
    void activeClassificationCoversOnlyNonterminalOperationStates() {
        assertTrue(EmployeeWorkstationOperationState.PREPARING.active());
        assertTrue(EmployeeWorkstationOperationState.OPERATING.active());
        assertTrue(EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION.active());
        assertFalse(EmployeeWorkstationOperationState.IDLE.active());
        assertFalse(EmployeeWorkstationOperationState.OPERATION_COMPLETE.active());
        assertFalse(EmployeeWorkstationOperationState.FAILURE.active());
    }
}
