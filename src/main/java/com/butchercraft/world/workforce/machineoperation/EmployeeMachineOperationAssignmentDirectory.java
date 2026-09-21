package com.butchercraft.world.workforce.machineoperation;

import java.util.List;
import java.util.Objects;

public record EmployeeMachineOperationAssignmentDirectory(
        int schemaVersion,
        long ownerRevision,
        long nextAssignmentSequence,
        List<EmployeeMachineOperationAssignment> assignments
) {
    public EmployeeMachineOperationAssignmentDirectory {
        if (schemaVersion != EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported employee machine-operation directory schema: "
                    + schemaVersion);
        }
        if (ownerRevision < 0L || nextAssignmentSequence <= 0L) {
            throw new IllegalArgumentException("Machine-operation directory revisions are invalid");
        }
        assignments = Objects.requireNonNull(assignments, "assignments").stream().sorted().toList();
        if (assignments.stream().map(EmployeeMachineOperationAssignment::assignmentId).distinct().count()
                != assignments.size()) {
            throw new IllegalArgumentException("Duplicate employee machine-operation assignment identity");
        }
        if (assignments.stream().mapToLong(EmployeeMachineOperationAssignment::revision).max().orElse(0L)
                > ownerRevision) {
            throw new IllegalArgumentException("Machine-operation owner revision regressed");
        }
        if (assignments.stream().mapToLong(EmployeeMachineOperationAssignment::assignmentSequence).max().orElse(0L)
                >= nextAssignmentSequence) {
            throw new IllegalArgumentException("Machine-operation assignment sequence allocator regressed");
        }
        long activeEmployees = assignments.stream().filter(EmployeeMachineOperationAssignment::active)
                .map(EmployeeMachineOperationAssignment::employeeId).distinct().count();
        long activeAssignments = assignments.stream().filter(EmployeeMachineOperationAssignment::active).count();
        if (activeEmployees != activeAssignments) {
            throw new IllegalArgumentException("Employee has multiple active machine-operation assignments");
        }
    }

    public static EmployeeMachineOperationAssignmentDirectory empty() {
        return new EmployeeMachineOperationAssignmentDirectory(
                EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION, 0L, 1L, List.of());
    }
}
