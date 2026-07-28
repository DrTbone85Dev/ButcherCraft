package com.butchercraft.world.workforce.employee;

import java.util.Objects;

public record EmployeeFailure(EmployeeFailureCode code, String detail) {
    public EmployeeFailure {
        code = Objects.requireNonNull(code, "code");
        detail = EmployeeValidation.requireText(detail, "failure detail");
    }
}
