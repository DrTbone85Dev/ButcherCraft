package com.butchercraft.world.workforce.machineoperation;

import java.util.Objects;

public record EmployeeMachineOperationFailure(EmployeeMachineOperationFailureCode code, String detail) {
    public EmployeeMachineOperationFailure {
        code = Objects.requireNonNull(code, "code");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty()) {
            throw new IllegalArgumentException("Machine-operation failure detail must not be blank");
        }
    }
}
