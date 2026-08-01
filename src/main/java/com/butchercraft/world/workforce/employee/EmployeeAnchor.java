package com.butchercraft.world.workforce.employee;

import java.util.Objects;

public record EmployeeAnchor(String dimensionIdentity, int x, int y, int z, int radius) {
    public EmployeeAnchor {
        dimensionIdentity = EmployeeValidation.requireIdentity(dimensionIdentity, "dimensionIdentity");
        if (radius < 1 || radius > 64) {
            throw new IllegalArgumentException("Employee anchor radius must be 1-64: " + radius);
        }
    }

    public boolean sameDimension(String candidateDimension) {
        return dimensionIdentity.equals(Objects.requireNonNull(candidateDimension, "candidateDimension"));
    }
}
