package com.butchercraft.workstation.reservation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationReservationManagerTest {
    @Test
    void reservationBindsOneEmployeeToOneWorkstation() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty();

        WorkstationReservationRecord record = manager.reserve(request("grinder", 1, "employee/1", 10L)).orThrow();

        assertEquals("butchercraft:workstation/grinder/minecraft/overworld/1/1/1", record.workstationIdentity());
        assertEquals("butchercraft:employee/1", record.employeeIdentity());
        assertEquals(WorkstationReservationState.EMPLOYEE_EN_ROUTE, record.state());
        assertTrue(manager.findByEmployee("butchercraft:employee/1").isPresent());
        assertTrue(manager.findByWorkstation(record.workstationIdentity()).isPresent());
    }

    @Test
    void duplicateSameEmployeeAndWorkstationObservesExistingReservation() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty();
        WorkstationReservationRecord first = manager.reserve(request("grinder", 1, "employee/1", 10L)).orThrow();

        WorkstationReservationRecord second = manager.reserve(request("grinder", 1, "employee/1", 20L)).orThrow();

        assertEquals(first, second);
        assertEquals(1, manager.activeReservations().size());
    }

    @Test
    void secondEmployeeCannotReserveOccupiedWorkstation() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty();
        manager.reserve(request("grinder", 1, "employee/1", 10L)).orThrow();

        WorkstationReservationResult<WorkstationReservationRecord> result =
                manager.reserve(request("grinder", 1, "employee/2", 11L));

        assertFalse(result.succeeded());
        assertEquals(WorkstationReservationFailureCode.WORKSTATION_ALREADY_RESERVED,
                result.failure().orElseThrow().code());
    }

    @Test
    void employeeCannotHoldTwoReservations() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty();
        manager.reserve(request("grinder", 1, "employee/1", 10L)).orThrow();

        WorkstationReservationResult<WorkstationReservationRecord> result =
                manager.reserve(request("patty_former", 2, "employee/1", 11L));

        assertFalse(result.succeeded());
        assertEquals(WorkstationReservationFailureCode.EMPLOYEE_ALREADY_RESERVED,
                result.failure().orElseThrow().code());
    }

    @Test
    void releaseAllowsAnotherEmployeeToReserve() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty();
        WorkstationReservationRecord first = manager.reserve(request("grinder", 1, "employee/1", 10L)).orThrow();

        WorkstationReservationRecord released = manager.releaseByEmployee(
                "butchercraft:employee/1",
                "test release"
        ).orThrow();
        WorkstationReservationRecord second = manager.reserve(request("grinder", 1, "employee/2", 12L)).orThrow();

        assertEquals(WorkstationReservationState.RELEASED, released.state());
        assertEquals(first.workstationIdentity(), second.workstationIdentity());
        assertEquals("butchercraft:employee/2", second.employeeIdentity());
    }

    @Test
    void arrivalAndDisplacementAreExplicitStateTransitions() {
        WorkstationReservationManager manager = WorkstationReservationManager.empty();
        WorkstationReservationRecord first = manager.reserve(request("grinder", 1, "employee/1", 10L)).orThrow();

        WorkstationReservationRecord arrived = manager.markArrived(
                first.employeeIdentity(),
                first.workstationIdentity()
        ).orElseThrow();
        WorkstationReservationRecord enRoute = manager.markEnRoute(
                first.employeeIdentity(),
                first.workstationIdentity()
        ).orElseThrow();

        assertEquals(WorkstationReservationState.EMPLOYEE_ARRIVED, arrived.state());
        assertEquals(WorkstationReservationState.EMPLOYEE_EN_ROUTE, enRoute.state());
    }

    @Test
    void loadReconciliationKeepsEarliestNonConflictingActiveRecords() {
        WorkstationReservationRecord first = WorkstationReservationRecord.enRoute(
                request("grinder", 1, "employee/1", 10L));
        WorkstationReservationRecord workstationConflict = WorkstationReservationRecord.enRoute(
                request("grinder", 1, "employee/2", 11L));
        WorkstationReservationRecord employeeConflict = WorkstationReservationRecord.enRoute(
                request("patty_former", 2, "employee/1", 12L));
        WorkstationReservationRecord independent = WorkstationReservationRecord.enRoute(
                request("patty_former", 3, "employee/3", 13L));

        WorkstationReservationManager manager = new WorkstationReservationManager(
                WorkstationReservationDirectory.of(List.of(
                        employeeConflict,
                        independent,
                        workstationConflict,
                        first
                ))
        );

        assertEquals(List.of(first, independent), manager.activeReservations());
    }

    private static WorkstationReservationRequest request(
            String type,
            int x,
            String employeePath,
            long createdTick
    ) {
        return new WorkstationReservationRequest(
                "butchercraft:workstation/" + type + "/minecraft/overworld/" + x + "/1/1",
                type,
                "butchercraft:" + employeePath,
                createdTick,
                "minecraft:overworld",
                x,
                1,
                1,
                x,
                1,
                0,
                1
        );
    }
}
