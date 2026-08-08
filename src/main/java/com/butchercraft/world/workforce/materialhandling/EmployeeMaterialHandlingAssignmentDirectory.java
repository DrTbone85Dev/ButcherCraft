package com.butchercraft.world.workforce.materialhandling;

import java.util.List;
import java.util.Objects;

public record EmployeeMaterialHandlingAssignmentDirectory(
        int schemaVersion,
        long ownerRevision,
        List<EmployeeMaterialHandlingAssignment> assignments
) {
    public EmployeeMaterialHandlingAssignmentDirectory {
        if (schemaVersion != EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported employee Material Handling directory schema: " + schemaVersion);
        }
        if (ownerRevision < 0L) {
            throw new IllegalArgumentException("Employee Material Handling owner revision must not be negative");
        }
        assignments = Objects.requireNonNull(assignments, "assignments").stream().sorted().toList();
        long maximumRevision = assignments.stream()
                .mapToLong(EmployeeMaterialHandlingAssignment::revision)
                .max()
                .orElse(0L);
        if (maximumRevision > ownerRevision) {
            throw new IllegalArgumentException("Employee Material Handling owner revision regressed");
        }
        long uniqueIds = assignments.stream().map(EmployeeMaterialHandlingAssignment::assignmentId).distinct().count();
        if (uniqueIds != assignments.size()) {
            throw new IllegalArgumentException("Duplicate employee Material Handling assignment identity");
        }
        long activeEmployees = assignments.stream()
                .filter(EmployeeMaterialHandlingAssignment::active)
                .map(EmployeeMaterialHandlingAssignment::employeeId)
                .distinct()
                .count();
        long activeAssignments = assignments.stream().filter(EmployeeMaterialHandlingAssignment::active).count();
        if (activeEmployees != activeAssignments) {
            throw new IllegalArgumentException("Employee has multiple active Material Handling assignments");
        }
    }

    public static EmployeeMaterialHandlingAssignmentDirectory empty() {
        return new EmployeeMaterialHandlingAssignmentDirectory(
                EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION,
                0L,
                List.of()
        );
    }
}
