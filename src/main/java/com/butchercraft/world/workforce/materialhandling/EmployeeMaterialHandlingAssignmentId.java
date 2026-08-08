package com.butchercraft.world.workforce.materialhandling;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.materialhandling.MaterialTransferId;
import com.butchercraft.world.workforce.employee.EmployeeId;

import java.util.Objects;

public record EmployeeMaterialHandlingAssignmentId(String value)
        implements Comparable<EmployeeMaterialHandlingAssignmentId> {
    private static final String PREFIX = "butchercraft:employee_material_handling_assignment/v1/";

    public EmployeeMaterialHandlingAssignmentId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!value.startsWith(PREFIX) || value.length() <= PREFIX.length()) {
            throw new IllegalArgumentException("Unsupported employee Material Handling assignment identity: " + value);
        }
    }

    public static EmployeeMaterialHandlingAssignmentId create(
            WorldIdentityRootIdentity worldIdentity,
            EmployeeId employeeId,
            MaterialTransferId transferId,
            String configurationIdentity
    ) {
        String digest = EmployeeMaterialHandlingDigest.sha256(String.join("\n",
                "employee_material_handling_assignment",
                Integer.toString(EmployeeMaterialHandlingAssignmentSchema.CURRENT_VERSION),
                Objects.requireNonNull(worldIdentity, "worldIdentity").identity(),
                Integer.toString(worldIdentity.schemaVersion()),
                worldIdentity.rootDigest(),
                Objects.requireNonNull(employeeId, "employeeId").value(),
                Objects.requireNonNull(transferId, "transferId").value(),
                requireIdentity(configurationIdentity, "configurationIdentity")
        ));
        return new EmployeeMaterialHandlingAssignmentId(PREFIX + digest.substring("sha256:".length(), 31));
    }

    static String requireIdentity(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(label + " must be a canonical identity");
        }
        return normalized;
    }

    @Override
    public int compareTo(EmployeeMaterialHandlingAssignmentId other) {
        return value.compareTo(other.value);
    }
}
