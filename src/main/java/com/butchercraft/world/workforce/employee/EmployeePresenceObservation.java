package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.BusinessId;

import java.util.Objects;
import java.util.Optional;

public record EmployeePresenceObservation(
        EmployeeId employeeId,
        BusinessId businessId,
        String displayName,
        EmployeeStatus status,
        EmployeePresenceState presenceState,
        Optional<EmployeeShiftAssignment> assignedShift,
        Optional<String> activeShiftIdentity,
        boolean plantOpen,
        String reason,
        long recordRevision
) {
    public EmployeePresenceObservation {
        employeeId = Objects.requireNonNull(employeeId, "employeeId");
        businessId = Objects.requireNonNull(businessId, "businessId");
        displayName = EmployeeValidation.requireText(displayName, "displayName");
        status = Objects.requireNonNull(status, "status");
        presenceState = Objects.requireNonNull(presenceState, "presenceState");
        assignedShift = Objects.requireNonNull(assignedShift, "assignedShift");
        activeShiftIdentity = Objects.requireNonNull(activeShiftIdentity, "activeShiftIdentity")
                .map(value -> EmployeeValidation.requireIdentity(value, "activeShiftIdentity"));
        reason = EmployeeValidation.requireText(reason, "reason");
        if (recordRevision < 0L) {
            throw new IllegalArgumentException("Employee observation revision must not be negative");
        }
    }
}
