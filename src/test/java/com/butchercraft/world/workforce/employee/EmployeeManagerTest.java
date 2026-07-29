package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.workforce.WorkforceRegistry;
import com.butchercraft.world.workforce.department.BuiltInDepartmentDefinitions;
import com.butchercraft.world.workforce.department.DepartmentId;
import com.butchercraft.world.workforce.department.DepartmentRegistry;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeManagerTest {
    @Test
    void createEmployeeAssignsDeterministicSequenceAndCapacityIsEnforced() {
        EmployeeManager manager = new EmployeeManager(EmployeeDirectory.empty(), 1);

        EmployeeOperationResult<EmployeeRecord> first = manager.createEmployee(
                com.butchercraft.world.identity.WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY),
                EmployeeTestFixtures.BUSINESS,
                Optional.empty(),
                Optional.of(EmployeeTestFixtures.dayShift()),
                Optional.empty(),
                EmployeeTestFixtures.calendar(0L, 7, 0),
                "butchercraft:employee_creation/test",
                EmployeeTestFixtures.BUSINESS_RUNTIME.identity().value()
        );
        EmployeeOperationResult<EmployeeRecord> second = manager.createEmployee(
                com.butchercraft.world.identity.WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY),
                EmployeeTestFixtures.BUSINESS,
                Optional.empty(),
                Optional.of(EmployeeTestFixtures.dayShift()),
                Optional.empty(),
                EmployeeTestFixtures.calendar(0L, 7, 0),
                "butchercraft:employee_creation/test",
                EmployeeTestFixtures.BUSINESS_RUNTIME.identity().value()
        );

        assertTrue(first.succeeded());
        assertEquals(0L, first.orThrow().sequence());
        assertFalse(second.succeeded());
        assertEquals(EmployeeFailureCode.CAPACITY_EXCEEDED, second.failure().orElseThrow().code());
    }

    @Test
    void terminatedEmployeeCannotBeReactivatedOrAssignedShift() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);

        assertTrue(manager.transitionStatus(record.employeeId(), EmployeeStatus.TERMINATED).succeeded());

        EmployeeOperationResult<EmployeeRecord> reactivated =
                manager.transitionStatus(record.employeeId(), EmployeeStatus.ACTIVE);
        EmployeeOperationResult<EmployeeRecord> reassigned =
                manager.assignShift(record.employeeId(), Optional.of(EmployeeTestFixtures.eveningShift()));

        assertFalse(reactivated.succeeded());
        assertEquals(EmployeeFailureCode.TERMINATED_EMPLOYEE, reactivated.failure().orElseThrow().code());
        assertFalse(reassigned.succeeded());
        assertEquals(EmployeeFailureCode.TERMINATED_EMPLOYEE, reassigned.failure().orElseThrow().code());
    }

    @Test
    void scheduledPresenceCannotBeWrittenDirectly() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);

        EmployeeOperationResult<EmployeeRecord> result =
                manager.setPresence(record.employeeId(), EmployeePresenceState.SCHEDULED);

        assertFalse(result.succeeded());
        assertEquals(EmployeeFailureCode.INVALID_PRESENCE_STATE, result.failure().orElseThrow().code());
    }

    @Test
    void invalidPositionAssignmentFailsExplicitly() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);

        EmployeeOperationResult<EmployeeRecord> result = manager.assignPosition(
                record.employeeId(),
                Optional.of(new com.butchercraft.world.workforce.PositionId("missing_position")),
                WorkforceRegistry.empty()
        );

        assertFalse(result.succeeded());
        assertEquals(EmployeeFailureCode.INVALID_POSITION, result.failure().orElseThrow().code());
    }

    @Test
    void departmentAssignmentStoresWorkforceOwnedReference() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);
        DepartmentRegistry departments = BuiltInDepartmentDefinitions.defaults(
                com.butchercraft.world.identity.WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY)
        ).registry();

        EmployeeRecord updated = manager.assignDepartment(
                record.employeeId(),
                Optional.of(DepartmentSchema.PROCESSING),
                departments
        ).orThrow();
        EmployeePresenceObservation observation = manager.observe(
                record.employeeId(),
                EmployeeTestFixtures.observe(0L, 7, 0),
                EmployeeTestFixtures.BUSINESS_RUNTIME
        ).orThrow();

        assertEquals(Optional.of(DepartmentSchema.PROCESSING), updated.assignedDepartmentId());
        assertEquals(Optional.of(DepartmentSchema.PROCESSING), observation.assignedDepartmentId());
    }

    @Test
    void invalidDepartmentAssignmentFailsExplicitly() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);

        EmployeeOperationResult<EmployeeRecord> result = manager.assignDepartment(
                record.employeeId(),
                Optional.of(new DepartmentId("unknown_department")),
                DepartmentRegistry.empty()
        );

        assertFalse(result.succeeded());
        assertEquals(EmployeeFailureCode.INVALID_DEPARTMENT, result.failure().orElseThrow().code());
    }

    @Test
    void presenceObservationFollowsActiveShiftAndExplicitPresence() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);

        EmployeePresenceObservation scheduled = manager.observe(
                record.employeeId(),
                EmployeeTestFixtures.observe(0L, 7, 0),
                EmployeeTestFixtures.BUSINESS_RUNTIME
        ).orThrow();
        manager.setPresence(record.employeeId(), EmployeePresenceState.PRESENT);
        EmployeePresenceObservation present = manager.observe(
                record.employeeId(),
                EmployeeTestFixtures.observe(0L, 7, 0),
                EmployeeTestFixtures.BUSINESS_RUNTIME
        ).orThrow();
        EmployeePresenceObservation offShift = manager.observe(
                record.employeeId(),
                EmployeeTestFixtures.observe(0L, 15, 0),
                EmployeeTestFixtures.BUSINESS_RUNTIME
        ).orThrow();

        assertEquals(EmployeePresenceState.SCHEDULED, scheduled.presenceState());
        assertEquals(EmployeePresenceState.PRESENT, present.presenceState());
        assertEquals(EmployeePresenceState.OFF_SHIFT, offShift.presenceState());
    }

    @Test
    void removedOrChangedShiftIdentityMakesEmployeeUnavailable() {
        EmployeeManager manager = EmployeeTestFixtures.manager();
        EmployeeRecord record = EmployeeTestFixtures.employee(manager);
        EmployeeShiftAssignment staleShift = new EmployeeShiftAssignment(
                record.assignedShift().orElseThrow().shiftId(),
                "butchercraft:business_shift/v1/0000000000000000000000000000000000000000000000000000000000000000",
                "Day Shift",
                record.assignedShift().orElseThrow().shiftSetIdentity(),
                record.assignedShift().orElseThrow().configurationIdentity()
        );

        manager.assignShift(record.employeeId(), Optional.of(staleShift));
        EmployeePresenceObservation observation = manager.observe(
                record.employeeId(),
                EmployeeTestFixtures.observe(0L, 7, 0),
                EmployeeTestFixtures.BUSINESS_RUNTIME
        ).orThrow();

        assertEquals(EmployeePresenceState.UNAVAILABLE, observation.presenceState());
    }
}
