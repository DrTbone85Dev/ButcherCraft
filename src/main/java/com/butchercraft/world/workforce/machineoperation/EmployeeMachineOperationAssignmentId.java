package com.butchercraft.world.workforce.machineoperation;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.workforce.employee.EmployeeId;

import java.util.Objects;

public record EmployeeMachineOperationAssignmentId(String value)
        implements Comparable<EmployeeMachineOperationAssignmentId> {
    private static final String PREFIX = "butchercraft:employee_machine_operation_assignment/v1/";

    public EmployeeMachineOperationAssignmentId {
        value = EmployeeMachineOperationDigest.requireIdentity(value, "machine-operation assignment identity");
        if (!value.startsWith(PREFIX) || value.length() <= PREFIX.length()) {
            throw new IllegalArgumentException("Unsupported employee machine-operation assignment identity");
        }
    }

    public static EmployeeMachineOperationAssignmentId create(
            WorldIdentityRootIdentity worldIdentity,
            long sequence,
            EmployeeId employeeId,
            WorkstationEndpointReference workstation,
            String configurationIdentity
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        if (sequence <= 0L) {
            throw new IllegalArgumentException("Machine-operation assignment sequence must be positive");
        }
        String digest = EmployeeMachineOperationDigest.sha256(String.join("\n",
                "employee_machine_operation_assignment",
                Integer.toString(EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION),
                worldIdentity.identity(),
                Integer.toString(worldIdentity.schemaVersion()),
                worldIdentity.rootDigest(),
                Long.toString(sequence),
                Objects.requireNonNull(employeeId, "employeeId").value(),
                Objects.requireNonNull(workstation, "workstation").instanceId().value(),
                Long.toString(workstation.generation()),
                EmployeeMachineOperationDigest.requireIdentity(configurationIdentity, "configurationIdentity")
        ));
        return new EmployeeMachineOperationAssignmentId(PREFIX + digest.substring("sha256:".length(), 31));
    }

    @Override
    public int compareTo(EmployeeMachineOperationAssignmentId other) {
        return value.compareTo(other.value);
    }
}
