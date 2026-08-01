package com.butchercraft.world.workforce.department;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInDepartmentDefinitionsTest {
    @Test
    void defaultDirectoryRegistersOnlyTheFiveCanonicalDepartments() {
        DepartmentDirectory directory = DepartmentTestFixtures.defaults();
        List<DepartmentId> ids = directory.registry().records().stream()
                .map(DepartmentRecord::departmentId)
                .toList();

        assertEquals(List.of(
                DepartmentSchema.MAINTENANCE,
                DepartmentSchema.OFFICE,
                DepartmentSchema.PACKAGING,
                DepartmentSchema.PROCESSING,
                DepartmentSchema.SHIPPING
        ), ids);
    }

    @Test
    void processingIsTheOnlyFunctionalDefaultDepartmentAnchor() {
        DepartmentDirectory directory = DepartmentTestFixtures.defaults();
        DepartmentAnchor processing = directory.registry()
                .find(DepartmentSchema.PROCESSING)
                .orElseThrow()
                .anchor()
                .orElseThrow();

        assertEquals("minecraft:overworld", processing.dimensionIdentity());
        assertEquals(DepartmentSchema.DEFAULT_PROCESSING_X, processing.x());
        assertEquals(DepartmentSchema.DEFAULT_PROCESSING_Y, processing.y());
        assertEquals(DepartmentSchema.DEFAULT_PROCESSING_Z, processing.z());
        assertEquals(DepartmentSchema.DEFAULT_PROCESSING_RADIUS, processing.radius());
        assertTrue(directory.registry().find(DepartmentSchema.PACKAGING).orElseThrow().anchor().isEmpty());
        assertTrue(directory.registry().find(DepartmentSchema.SHIPPING).orElseThrow().anchor().isEmpty());
        assertTrue(directory.registry().find(DepartmentSchema.OFFICE).orElseThrow().anchor().isEmpty());
        assertTrue(directory.registry().find(DepartmentSchema.MAINTENANCE).orElseThrow().anchor().isEmpty());
    }
}
