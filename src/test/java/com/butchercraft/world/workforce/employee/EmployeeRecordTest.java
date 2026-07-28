package com.butchercraft.world.workforce.employee;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeRecordTest {
    @Test
    void hiredRecordStoresEmploymentIdentityShiftAndHireBusinessTimestamp() {
        EmployeeRecord record = EmployeeTestFixtures.employee(EmployeeTestFixtures.manager());

        assertEquals(EmployeeStatus.ACTIVE, record.status());
        assertEquals(EmployeePresenceState.OFF_SHIFT, record.presenceState());
        assertEquals("Ada Cutter", record.displayName());
        assertTrue(record.assignedShift().isPresent());
        assertEquals(0L, record.hireBusinessDay());
        assertEquals("07:00", record.hireBusinessTime().displayText());
        assertEquals(1L, record.recordRevision());
    }

    @Test
    void inactiveStatusForcesUnavailablePresence() {
        EmployeeRecord record = EmployeeTestFixtures.employee(EmployeeTestFixtures.manager());

        EmployeeRecord inactive = record.withStatus(EmployeeStatus.INACTIVE);

        assertEquals(EmployeeStatus.INACTIVE, inactive.status());
        assertEquals(EmployeePresenceState.UNAVAILABLE, inactive.presenceState());
    }

    @Test
    void nonActiveRecordCannotBePersistedWithPresentPresence() {
        EmployeeRecord record = EmployeeTestFixtures.employee(EmployeeTestFixtures.manager());

        assertThrows(IllegalArgumentException.class, () -> new EmployeeRecord(
                EmployeeSchema.CURRENT_VERSION,
                record.employeeId(),
                record.businessId(),
                record.sequence(),
                record.worldIdentityRoot(),
                record.worldIdentityRootDigest(),
                record.displayName(),
                Optional.empty(),
                EmployeeStatus.TERMINATED,
                EmployeePresenceState.PRESENT,
                record.assignedShift(),
                Optional.empty(),
                record.hireBusinessDay(),
                record.hireBusinessTime(),
                record.hireWorldDayIdentity(),
                Optional.empty(),
                Optional.empty(),
                record.recordRevision(),
                record.creationSourceIdentity(),
                record.creationConfigurationIdentity()
        ));
    }
}
