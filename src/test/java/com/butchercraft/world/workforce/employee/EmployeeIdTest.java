package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeIdTest {
    @Test
    void employeeIdentityIsDeterministicForSameWorldBusinessSequenceAndSource() {
        var root = WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY);

        EmployeeId first = EmployeeId.from(root, EmployeeTestFixtures.BUSINESS.id(), 7L,
                "butchercraft:employee_creation/test");
        EmployeeId second = EmployeeId.from(root, EmployeeTestFixtures.BUSINESS.id(), 7L,
                "butchercraft:employee_creation/test");

        assertEquals(first, second);
        assertTrue(first.value().startsWith("butchercraft:employee/v1/"));
    }

    @Test
    void sequenceAndCreationSourceChangeEmployeeIdentity() {
        var root = WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY);

        EmployeeId first = EmployeeId.from(root, EmployeeTestFixtures.BUSINESS.id(), 7L,
                "butchercraft:employee_creation/test");
        EmployeeId differentSequence = EmployeeId.from(root, EmployeeTestFixtures.BUSINESS.id(), 8L,
                "butchercraft:employee_creation/test");
        EmployeeId differentSource = EmployeeId.from(root, EmployeeTestFixtures.BUSINESS.id(), 7L,
                "butchercraft:employee_creation/other");

        assertNotEquals(first, differentSequence);
        assertNotEquals(first, differentSource);
    }

    @Test
    void generatedNamesAreDeterministicAndSequenceVisible() {
        var root = WorldIdentityRootIdentities.from(EmployeeTestFixtures.WORLD_IDENTITY);

        String first = EmployeeNameGenerator.generatedDisplayName(root, EmployeeTestFixtures.BUSINESS.id(), 3L);
        String second = EmployeeNameGenerator.generatedDisplayName(root, EmployeeTestFixtures.BUSINESS.id(), 3L);

        assertEquals(first, second);
        assertTrue(first.endsWith("4"));
    }
}
