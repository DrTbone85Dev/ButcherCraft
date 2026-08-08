package com.butchercraft.world.workforce.materialhandling;

import java.util.Objects;

public record EmployeeMaterialHandlingFailure(EmployeeMaterialHandlingFailureCode code, String detail) {
    public EmployeeMaterialHandlingFailure {
        code = Objects.requireNonNull(code, "code");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty() || detail.length() > 512) {
            throw new IllegalArgumentException("Employee Material Handling failure detail must contain 1-512 characters");
        }
    }
}
